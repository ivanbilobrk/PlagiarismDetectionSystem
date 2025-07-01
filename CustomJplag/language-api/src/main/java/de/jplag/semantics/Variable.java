package de.jplag.semantics;

/**
 * Each variable has its unique identity, important for tracing in graph (NormalizationGraph::spreadKeep).
 */
public class Variable {
    public final String name;
    public final VariableScope scope;
    public final boolean isMutable;

    public Variable(String name, VariableScope scope, boolean isMutable) {
        this.name = name;
        this.scope = scope;
        this.isMutable = isMutable;
    }

    boolean isMutable() {
        return isMutable;
    }

    @Override
    public String toString() {
        return name + (isMutable ? "*" : "") + " [scope: " + scope.name().toLowerCase() + "]";
    }
}
