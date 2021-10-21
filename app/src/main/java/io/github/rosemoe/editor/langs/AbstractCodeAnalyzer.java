package io.github.rosemoe.editor.langs;

import io.github.rosemoe.editor.interfaces.CodeAnalyzer;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Diagnostic;
import java.util.Map;
import java.util.HashMap;

public abstract class AbstractCodeAnalyzer implements CodeAnalyzer {
    
    @Override
    public Diagnostic findDiagnosticContaining(int line, int column) {
        return null;
    }

    @Override
    public List<Diagnostic> findDiagnosticsContainingLine(int line) {
        return new ArrayList<Diagnostic>();
    }

    @Override
    public Map<Integer, Diagnostic> getDiagnosticsAtLine(int line) {
        return new HashMap<Integer, Diagnostic>();
    }
}
