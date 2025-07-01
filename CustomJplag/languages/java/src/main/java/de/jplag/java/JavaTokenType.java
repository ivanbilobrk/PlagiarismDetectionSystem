package de.jplag.java;

import de.jplag.TokenType;

import java.util.HashMap;
import java.util.Map;

public enum JavaTokenType implements TokenType {
    J_PACKAGE("PACKAGE"),
    J_IMPORT("IMPORT"),
    J_CLASS_BEGIN("CLASS{"),
    J_CLASS_END("}CLASS"),
    J_METHOD_BEGIN("METHOD{"),
    J_METHOD_END("}METHOD"),
    J_VARDEF("VARDEF"),
    J_SYNC_BEGIN("SYNC{"),
    J_SYNC_END("}SYNC"),
    J_LOOP_BEGIN("LOOP{"),
    J_LOOP_END("}LOOP"),
    J_SWITCH_BEGIN("SWITCH{"),
    J_SWITCH_END("}SWITCH"),
    J_CASE("CASE"),
    J_TRY_BEGIN("TRY{"),
    J_TRY_END("}TRY"),
    J_CATCH_BEGIN("CATCH{"),
    J_CATCH_END("}CATCH"),
    J_FINALLY_BEGIN("FINALLY{"),
    J_FINALLY_END("}FINALLY"),
    J_IF_BEGIN("IF{"),
    J_IF_END("}IF"),
    J_COND("COND"),
    J_BREAK("BREAK"),
    J_CONTINUE("CONTINUE"),
    J_RETURN("RETURN"),
    J_THROW("THROW"),
    J_IN_CLASS_BEGIN("INCLASS{"),
    J_IN_CLASS_END("}INCLASS"),
    J_APPLY("APPLY"),
    J_NEWCLASS("NEWCLASS"),
    J_NEWARRAY("NEWARRAY"),
    J_ASSIGN("ASSIGN"),
    J_INTERFACE_BEGIN("INTERF{"),
    J_INTERFACE_END("}INTERF"),
    J_CONSTR_BEGIN("CONSTR{"),
    J_CONSTR_END("}CONSTR"),
    J_VOID("VOID"),
    J_ARRAY_INIT_BEGIN("ARRINIT{"),
    J_ARRAY_INIT_END("}ARRINIT"),
    J_ENUM_BEGIN("ENUM"),
    J_ENUM_CLASS_BEGIN("ENUM_CLA"),
    J_ENUM_END("}ENUM"),
    J_GENERIC("GENERIC"),
    J_ASSERT("ASSERT"),
    J_ANNO("ANNO"),
    J_ANNO_MARKER("ANNOMARK"),
    J_ANNO_M_BEGIN("ANNO_M{"),
    J_ANNO_M_END("}ANNO_M"),
    J_ANNO_T_BEGIN("ANNO_T{"),
    J_ANNO_T_END("}ANNO_T"),
    J_ANNO_C_BEGIN("ANNO_C{"),
    J_ANNO_C_END("}ANNO_C"),
    J_REQUIRES("REQUIRES"),
    J_PROVIDES("PROVIDES"),
    J_EXPORTS("EXPORTS"),
    J_MODULE_BEGIN("MODULE{"),
    J_MODULE_END("}MODULE"),
    J_YIELD("YIELD"),
    J_DEFAULT("DEFAULT"),
    J_RECORD_BEGIN("RECORD{"),
    J_RECORD_END("}RECORD");

    private final String description;

    JavaTokenType(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    private static final Map<String, JavaTokenType> tokenMap = new HashMap<>();

    static {
        for (JavaTokenType type : JavaTokenType.values()) {
            tokenMap.put(type.getDescription(), type);
        }
    }

    public static TokenType getTokenType(String token) {
        return tokenMap.get(token);
    }
}
