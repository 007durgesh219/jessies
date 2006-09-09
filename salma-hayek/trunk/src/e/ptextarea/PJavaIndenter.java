package e.ptextarea;

public class PJavaIndenter extends PSimpleIndenter {
    public PJavaIndenter(PTextArea textArea) {
        super(textArea);
    }
    
    protected boolean isLabel(String activePartOfLine) {
        return isSwitchLabel(activePartOfLine);
    }
    
    protected boolean shouldMoveHashToColumnZero() {
        return false;
    }
    protected boolean shouldMoveLabels() {
        return true;
    }
    protected boolean shouldContinueDocComments() {
        return true;
    }
}
