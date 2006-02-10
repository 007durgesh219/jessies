#ifndef PTY_GENERATOR_H_included
#define PTY_GENERATOR_H_included

#include "DirectoryIterator.h"
#include "toString.h"
#include "unix_exception.h"

#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#if defined(__sun__)
#include <sys/stropts.h>
#endif

#include <deque>
#include <iostream>
#include <stdexcept>
#include <string>

class PtyGenerator {
    std::string ptyName;
    int masterFd;
    
public:
    PtyGenerator() : masterFd(-1) {
    }
    
    virtual ~PtyGenerator() {
    }
    
    std::string getSlavePtyName() {
        return ptyName;
    }
    
    int openMaster() {
        masterFd = ptym_open(ptyName);
        return masterFd;
    }
    
    int openSlaveAndCloseMaster() {
        struct group* grptr = getgrnam("tty");
        gid_t gid = (grptr != NULL) ? grptr->gr_gid : gid_t(-1);
        
        const char *cName = ptyName.c_str();
        chown(cName, getuid(), gid);
        chmod(cName, S_IRUSR | S_IWUSR | S_IWGRP);
        
        int fds = open(cName, O_RDWR | O_NOCTTY);
        if (fds < 0) {
            throw unix_exception("open(" + toString(cName) + ", O_RDWR | O_NOCTTY) failed");
        }
        close(masterFd);
        return fds;
    }
    
    pid_t forkAndExec(char * const *cmd, const char* workingDirectory) {
        pid_t pid = fork();
        if (pid < 0) {
            return -1;
        } else if (pid == 0) {
            try {
                runChild(cmd, workingDirectory, *this);  // Should never return.
            } catch (const std::exception& ex) {
                std::cerr << ex.what() << std::endl;
            }
            exit(1); // We're only exit()ing the child, not the VM.
        } else {
            return pid;
        }
    }
    
private:
    int search_for_pty(std::string& pts_name) {
        for (char* ptr1 = "pqrstuvwxyzPQRST"; *ptr1 != 0; ++ptr1) {
            for (char* ptr2 = "0123456789abcdef"; *ptr2 != 0; ++ptr2) {
                pts_name = "/dev/pty";
                pts_name.append(1, *ptr1);
                pts_name.append(1, *ptr2);
                
                /* try to open master */
                int fdm = open(pts_name.c_str(), O_RDWR);
                if (fdm < 0) {
                    if (errno == ENOENT) {
                        /* Different from EIO. */
                        throw std::runtime_error("Out of pseudo-terminal devices");
                    } else {
                        /* Try next pty device. */
                        continue;
                    }
                }
                
                /* Return name of slave. */
                pts_name = "/dev/tty";
                pts_name.append(1, *ptr1);
                pts_name.append(1, *ptr2);
                
                /* Return fd of master. */
                return fdm;
            }
        }
        throw std::runtime_error("Out of pseudo-terminal devices");
    }
    
    int ptym_open(std::string& pts_name) {
        int ptmx_fd = open("/dev/ptmx", O_RDWR);
        if (ptmx_fd < 0) {
            return search_for_pty(pts_name);
        }
        
        const char* name = ptsname(ptmx_fd);
        if (name == 0) {
            throw unix_exception("ptsname(" + toString(ptmx_fd) + ") failed");
        }
        pts_name = name;
        
        if (grantpt(ptmx_fd) != 0) {
            throw unix_exception("grantpt(" + toString(name) + ") failed");
        }
        if (unlockpt(ptmx_fd) != 0) {
            throw unix_exception("unlockpt(" + toString(name) + ") failed");
        }
        return ptmx_fd;
    }
    
    class child_exception : public unix_exception {
    public:
        child_exception(const std::string& message)
        : unix_exception("Error from child: " + message) {
        }
    };
    
    class child_exception_via_pipe : child_exception {
    public:
        child_exception_via_pipe(int pipeFd, const std::string& message)
        : child_exception(message) {
            // Take special measures to ensure that the error message is displayed,
            // given that std::err may not be working at this point.
            FILE* tunnel = fdopen(pipeFd, "w");
            fprintf(tunnel, "%s\n", what());
        }
    };
    
    static void runChild(char * const *cmd, const char* workingDirectory, PtyGenerator& ptyGenerator) {
        if (workingDirectory != 0) {
            if (chdir(workingDirectory) < 0) {
                throw child_exception(std::string() + "chdir(\"" + workingDirectory + "\")");
            }
        }
        if (setsid() < 0) {
            throw child_exception("setsid()");
        }

        int childFd = ptyGenerator.openSlaveAndCloseMaster();

#if defined(TIOCSCTTY)
        /* Give up the controlling terminal. */
        if (ioctl(childFd, TIOCSCTTY, 0) < 0) {
            throw child_exception_via_pipe(childFd, "ioctl(" + toString(childFd) + ", TIOCSCTTY, 0)");
        }
#endif

#if defined(__sun__)
        /* This seems to be necessary on Solaris to make STREAMS behave. */
        ioctl(childFd, I_PUSH, "ptem");
        ioctl(childFd, I_PUSH, "ldterm");
        ioctl(childFd, I_PUSH, "ttcompat");
#endif

        /* Slave becomes stdin/stdout/stderr of child. */
        if (childFd != STDIN_FILENO && dup2(childFd, STDIN_FILENO) != STDIN_FILENO) {
            throw child_exception_via_pipe(childFd, "dup2(" + toString(childFd) + ", STDIN_FILENO)");
        }
        if (childFd != STDOUT_FILENO && dup2(childFd, STDOUT_FILENO) != STDOUT_FILENO) {
            throw child_exception_via_pipe(childFd, "dup2(" + toString(childFd) + ", STDOUT_FILENO)");
        }
        if (childFd != STDERR_FILENO && dup2(childFd, STDERR_FILENO) != STDERR_FILENO) {
            throw child_exception_via_pipe(childFd, "dup2(" + toString(childFd) + ", STDERR_FILENO)");
        }
        if (childFd > STDERR_FILENO) {
            close(childFd);
        }
        closeUnusedFiles();
        fixEnvironment();
        
        /*
         * rxvt resets these signal handlers, and we'll do the same, because it magically
         * fixes the bug where ^c doesn't work if we're launched from KDE or Gnome's
         * launcher program.  I don't quite understand why - maybe bash reads the existing
         * SIGINT setting, and if it's set to something other than DFL it lets the parent process
         * take care of job control.
         */
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        
        execvp(cmd[0], cmd);
        throw unix_exception("Can't execute '" + toString(cmd[0]) + "'");
    }
    
    static void fixEnvironment() {
        // Tell the world which terminfo entry to use.
        putenv("TERM=terminator");
        
        // X11 terminal emulators set this.
        // http://elliotth.blogspot.com/2005/12/why-terminator-doesnt-support-windowid.html
        unsetenv("WINDOWID");
        
#ifdef __APPLE__
        // Apple's Java launcher uses environment variables to implement the -Xdock options.
        pid_t ppid = getppid();
        unsetenv(("APP_ICON_" + toString(ppid)).c_str());
        unsetenv(("APP_NAME_" + toString(ppid)).c_str());
        unsetenv(("JAVA_MAIN_CLASS_" + toString(ppid)).c_str());
        
        // Apple's Terminal sets these, and some programs/scripts identify Terminal this way.
        // In real life, these shouldn't be set, but they will be if we're debugging Terminator and running it from Terminal.
        // It's always confusing when programs behave differently during debugging!
        unsetenv("TERM_PROGRAM");
        unsetenv("TERM_PROGRAM_VERSION");
#else
        // Similarly, GNOME's Terminal sets this.
        unsetenv("COLORTERM");
#endif
    }
    
    /**
     * This allows the terminator-server-port socket to close when terminator quits
     * while a child is still running.
     * 
     * It also ensures that child processes don't have file descriptors for
     * files the Java VM has open (it typically has many).
     */
    static void closeUnusedFiles() {
        // A common idiom for closing the parent's file descriptors in a child is to close all possible file descriptors.
        // Sun 4843136 refers to this technique as a "stress test for the OS", pointing out that a system may have a high, or no, limit.
        // Sun 4413680 claims that the equivalent code in the JVM before 1.4.0_03 was a performance problem on Solaris.
        // Solaris offers closefrom(3), though none of our other platforms appears to.
        // BSD offers fcntl(F_CLOSEM) but none of our platforms appears to.
        
        // On Cygwin, Linux, and Solaris, a better solution iterates over "/proc/self/fd/".
        std::string fdDirectory("/proc/self/fd");
#ifdef __APPLE__
        // On Mac OS, there's "/dev/fd/" (which Linux seems to link to "/proc/self/fd/", but which on Solaris appears to be something quite different).
        fdDirectory = "/dev/fd";
#endif
        
        // There's no portable way to get the opendir(3) file descriptor, so we use two passes.
        // Pass 1: collect the fds to close.
        typedef std::deque<int> FdList;
        FdList fds;
        for (DirectoryIterator it(fdDirectory); it.isValid(); ++it) {
            int fd = strtoul(it->getName().c_str(), NULL, 10);
            if (fd > STDERR_FILENO) {
                fds.push_back(fd);
            }
        }
        
        // Pass 2: close the fds.
        for (FdList::const_iterator it = fds.begin(); it != fds.end(); ++it) {
            // The close of the opendir(3) file descriptor will fail, but we ignore that.
            close(*it);
        }
    }
};

#endif
