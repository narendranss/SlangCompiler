package com.slang.parser;

import com.slang.Type;
import com.slang.TypeCategory;
import com.slang.ast.*;
import com.slang.lexer.Lexer;

import java.util.*;

/**
 * Created by sarath on 16/3/17.
 */
public class Parser {

    private final Lexer lexer;

    private long lambdaCount = 0;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
    }

    public Module parseModule() {
        return new Module(parseFunctions());
    }

    public Map<String, Function> parseFunctions() {
        Token token = null;
        Map<String, Function> functions = new LinkedHashMap<>();
        do {
            Function function = parseFunction();
            functions.put(function.getName(), function);
            token = lexer.getCurrentToken();
        } while (Token.UNKNOWN != token);
        return functions;
    }

    public Function parseFunction() {
        //First call to parseFunction require eat and once parsing has started we should not call eat() as it will
        //skip a token
        if (null == lexer.getPreviousToken()) {
            lexer.eat();
        }
        lexer.expect(Token.FUNCTION);
        lexer.eat();
        Type returnType = parseType();

        lexer.eat();
        if (lexer.getCurrentToken() != Token.VAR_NAME) {
            throw new RuntimeException("Function name expected");
        }
        String name = lexer.getVariableName();
        lexer.eat();
        lexer.expect(Token.OPAR);

        LinkedHashMap<String, Type> formalArguments = new LinkedHashMap<>();

        lexer.eat();
        List<Type> fnFormalParamTypes = new ArrayList<>();
        while (lexer.getCurrentToken() != Token.CPAR) {
            Type varType = parseType();
            fnFormalParamTypes.add(varType);
            lexer.eat();
            if (lexer.getCurrentToken() != Token.VAR_NAME) {
                throw new RuntimeException("Formal parameter name expected");
            }
            String varName = lexer.getVariableName();
            lexer.eat();

            formalArguments.put(varName, varType);

            if (lexer.getCurrentToken() != Token.COMMA) {
                break;
            }
            lexer.eat();
        }

        lexer.expect(Token.CPAR);
        lexer.eat();
        List<Statement> functionBody = new ArrayList<>();

        boolean foundReturn = false;
        do {
            Statement statement = parseStatement();
            if(ReturnStatement.class.isAssignableFrom(statement.getClass())) {
                foundReturn = true;
            }
            functionBody.add(statement);
        } while (lexer.getCurrentToken() != Token.END);

        if (Type.VOID != returnType && !foundReturn) {
            throw new RuntimeException("Return getType expected");
        } else if (Type.VOID == returnType && !foundReturn) {
            functionBody.add(new ReturnStatement(new VoidExpression()));
        }
        lexer.expect(Token.END);

        //Build lambda getType information
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        int i = 0;
        int formalArgSize = formalArguments.entrySet().size();
        for(Map.Entry<String, Type> formalArgEntry : formalArguments.entrySet()) {
            sb.append(formalArgEntry.getValue().getTypeName());
            if(i++ != formalArgSize -1) {
                sb.append(",");
            }
        }
        sb.append(")->");
        sb.append(returnType.getTypeName());
        Function function = new Function(name, returnType, formalArguments, functionBody,
                new Type(sb.toString(), TypeCategory.FUNCTION, fnFormalParamTypes, returnType));
        lexer.eat();
        return function;

    }

    private Type parseType() {
        switch (lexer.getCurrentToken()) {
            case VOID:
                return Type.VOID;
            case INT:
                return Type.INTEGER;
            case LONG:
                return Type.LONG;
            case FLOAT:
                return Type.FLOAT;
            case DOUBLE:
                return Type.DOUBLE;
            case BOOL:
                return Type.BOOL;
            case STRING:
                return Type.STRING;
        }

        if (lexer.getCurrentToken() == Token.OPAR) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            lexer.eat();
            List<Type> fnFormalParamTypes = new ArrayList<>();
            while(lexer.getCurrentToken() != Token.CPAR) {
                Type type  = parseType();
                fnFormalParamTypes.add(type);
                sb.append(type.getTypeName());
                lexer.eat();
                if (lexer.getCurrentToken() != Token.COMMA) {
                    break;
                }
                sb.append(",");
                lexer.eat();
            }
            lexer.expect(Token.CPAR);
            sb.append(")");
            lexer.eat();
            lexer.expect(Token.SUB);
            sb.append("-");
            lexer.eat();
            lexer.expect(Token.GT);
            sb.append(">");
            lexer.eat();
            Type lambdaReturnType = parseType();
            sb.append(lambdaReturnType.getTypeName());
            return new Type(sb.toString(), TypeCategory.FUNCTION, fnFormalParamTypes, lambdaReturnType);
        } else {
            throw new RuntimeException("Return getType cannot be " + lexer.getCurrentToken());
        }
    }

    public List<Statement> parseStatements() {
        Token token = null;
        List<Statement> statements = new ArrayList<>();
        do {
            statements.add(parseStatement());
            token = lexer.getCurrentToken();
        } while (Token.UNKNOWN != token);
        return statements;
    }

    public Statement parseStatement() {
        //First call to parseStatement require eat and once parsing has started we should not call eat() as it will
        //skip a token
        if (null == lexer.getPreviousToken()) {
            lexer.eat();
        }
        Token token = lexer.getCurrentToken();

        //TODO accept function invocation statement with print
        if(Token.PRINT == token) {
            Expression expression = parseExpression();
            lexer.expect(Token.SEMICLN);
            lexer.eat();
            return new PrintStatement(expression);
        }
        //TODO accept function invocation statement with println
        if(Token.PRINTLN == token) {
            Expression expression = parseExpression();
            lexer.expect(Token.SEMICLN);
            lexer.eat();
            return new PrintlnStatement(expression);
        }

        if(Token.VAR == token) {
            Expression expression = parseExpression();
            VariableExpression variableExpression = (VariableExpression) expression;

            if(Token.SEMICLN == lexer.getCurrentToken()) {
                lexer.eat();
                return new VariableDeclarationStatement(variableExpression);
            } else if(Token.EQ == lexer.getCurrentToken()) {
                try {
                    Expression rhsExp = parseExpression();
                    if (lexer.getPreviousToken() == Token.VAR_NAME && lexer.getCurrentToken() == Token.OPAR) {
                        return new VariableDeclAndAssignStatement(new VariableDeclarationStatement(variableExpression),
                                new VariableAssignmentStatement(variableExpression.getVariableName(), parseFunctionInvocationExpression()));
                    } else {
                        lexer.expect(Token.SEMICLN);
                        lexer.eat();
                        return new VariableDeclAndAssignStatement(new VariableDeclarationStatement(variableExpression),
                                new VariableAssignmentStatement(variableExpression.getVariableName(), rhsExp));
                    }
                } catch (RuntimeException ex) {
                    if(lexer.getCurrentToken() == Token.LAMBDA) {
                        return new VariableDeclAndAssignStatement(new VariableDeclarationStatement(variableExpression),
                                new VariableAssignmentStatement(variableExpression.getVariableName(), parseLambdaExpression()));
                    }
                    throw new RuntimeException("Expected token is " + Token.LAMBDA + ", but got " + lexer.getCurrentToken());
                }
            }

        }

        if(Token.VAR_NAME == token) {
            String varName = lexer.getVariableName();
            lexer.eat();

            //function invocation
            if(lexer.getCurrentToken() == Token.OPAR) {
                return new FunctionInvokeStatement((FunctionInvokeExpression) parseFunctionInvocationExpression());
            //variable assignment
            } else if (lexer.getCurrentToken() == Token.EQ) {
                try {
                    Expression expression = parseExpression();
                    //Function invocation and assignment together
                    if(lexer.getPreviousToken() == Token.VAR_NAME && lexer.getCurrentToken() == Token.OPAR) {
                        return new VariableAssignmentStatement(varName, parseFunctionInvocationExpression());
                    } else {
                        VariableAssignmentStatement variableAssignmentStatement = new VariableAssignmentStatement(varName, expression);
                        lexer.expect(Token.SEMICLN);
                        lexer.eat();
                        return variableAssignmentStatement;
                    }
                } catch (RuntimeException ex) {
                    return new VariableAssignmentStatement(varName, parseLambdaExpression());
                }

            }

            throw new RuntimeException("Illega token " + lexer.getCurrentToken());

        }

        if(Token.IF == token) {
            return parseIfStatement();
        }

        if(Token.WHILE == token) {
            return parseWhileStatement();
        }

        if(Token.BREAK == token) {
            lexer.eat();
            lexer.expect(Token.SEMICLN);
            lexer.eat();
            return new BreakStatement();
        }

        if(Token.RETURN == token) {
            return parseReturnStatement();

        }

        throw new RuntimeException("Unexpected token : " + lexer.getCurrentToken());

    }

    private Expression parseLambdaExpression() {
        lexer.eat();
        Type returnType = parseType();

        lexer.eat();
        lexer.expect(Token.OPAR);

        LinkedHashMap<String, Type> formalArguments = new LinkedHashMap<>();

        lexer.eat();
        List<Type> fnFormalParamTypes = new ArrayList<>();
        while (lexer.getCurrentToken() != Token.CPAR) {
            Type varType = parseType();
            fnFormalParamTypes.add(varType);
            lexer.eat();
            if (lexer.getCurrentToken() != Token.VAR_NAME) {
                throw new RuntimeException("Formal parameter name expected");
            }
            String varName = lexer.getVariableName();
            lexer.eat();

            formalArguments.put(varName, varType);

            if (lexer.getCurrentToken() != Token.COMMA) {
                break;
            }
            lexer.eat();
        }

        lexer.expect(Token.CPAR);
        lexer.eat();
        List<Statement> functionBody = new ArrayList<>();

        boolean foundReturn = false;
        do {
            Statement statement = parseStatement();
            if(ReturnStatement.class.isAssignableFrom(statement.getClass())) {
                foundReturn = true;
            }
            functionBody.add(statement);
        } while (lexer.getCurrentToken() != Token.ENDLAMBDA);

        if (Type.VOID != returnType && !foundReturn) {
            throw new RuntimeException("Return getType expected");
        } else if (Type.VOID == returnType && !foundReturn) {
            functionBody.add(new ReturnStatement(new VoidExpression()));
        }
        lexer.expect(Token.ENDLAMBDA);

        //Build lambda getType information
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        int i = 0;
        int formalArgSize = formalArguments.entrySet().size();
        for(Map.Entry<String, Type> formalArgEntry : formalArguments.entrySet()) {
            sb.append(formalArgEntry.getValue().getTypeName());
            if(i++ != formalArgSize -1) {
                sb.append(",");
            }
        }
        sb.append(")->");
        sb.append(returnType.getTypeName());

        Function function = new Function("lambda$"+ (++lambdaCount), returnType, formalArguments,
                functionBody, new Type(sb.toString(), TypeCategory.FUNCTION, fnFormalParamTypes, returnType));
        lexer.eat();
        return new LambdaExpression(function);

    }

    private Statement parseIfStatement() {
        Expression expression = parseExpression();

        lexer.expect(Token.THEN);

        lexer.eat();

        //If no body
        if(lexer.getCurrentToken() == Token.ENDIF) {
            throw new RuntimeException("Empty If condition is not allowed");
        }

        List<Statement> trueBody = new ArrayList<>();

        do {
            Statement statement = parseStatement();
            trueBody.add(statement);
        } while (lexer.getCurrentToken() != Token.ENDIF && lexer.getCurrentToken() != Token.ELSE);

        List<Statement> falseBody = new ArrayList<>();

        //IF false part exists
        if(lexer.getCurrentToken() == Token.ELSE) {
            lexer.eat();
            do {
                Statement statement = parseStatement();
                falseBody.add(statement);
            } while (lexer.getCurrentToken() != Token.ENDIF);
        }
        lexer.expect(Token.ENDIF);
        lexer.eat();
        return new IfStatement(expression, trueBody, falseBody);
    }

    private Statement parseWhileStatement() {
        Expression expression = parseExpression();

        if(lexer.getCurrentToken() == Token.WEND ) {
            throw new RuntimeException("Empty loop is not allowed");
        }

        List<Statement> body = new ArrayList<>();

        do {
            Statement statement = parseStatement();
            body.add(statement);
        } while (lexer.getCurrentToken() != Token.WEND);

        lexer.expect(Token.WEND);

        lexer.eat();
        return new WhileStatement(expression, body);
    }

    private Statement parseReturnStatement() {
        //Again another hack to get parsing right
        try {
            Expression expression = parseExpression();
            lexer.expect(Token.SEMICLN);
            lexer.eat();
            return new ReturnStatement(expression);
        } catch (RuntimeException ex) {
            if(lexer.getCurrentToken() == Token.SEMICLN) {
                lexer.eat();
                return new ReturnStatement(new VoidExpression());
            } else {
                return new ReturnStatement(parseFunctionInvocationExpression());
            }

        }
    }

    private Expression parseFunctionInvocationExpression() {
        String functionName = lexer.getVariableName();

        if (lexer.getCurrentToken() == Token.OPAR) {

            List<Expression> actualParams = new ArrayList<>();

            while (lexer.getCurrentToken() != Token.CPAR) {
                //hack to get parsing right
                try {
                    actualParams.add(parseExpression());
                } catch (RuntimeException ex2) {
                    //TODO think of alternative ways to parse
                    if (lexer.getCurrentToken() == Token.CPAR) {
                        break;
                    }
                }
                if (lexer.getCurrentToken() != Token.COMMA) {
                    break;
                }
            }


            lexer.expect(Token.CPAR);
            lexer.eat();

            lexer.expect(Token.SEMICLN);
            lexer.eat();

            return new FunctionInvokeExpression(functionName, actualParams);

        }

        throw new RuntimeException("Unsupported token " + lexer.getCurrentToken());
    }

    private Type getType(Token currentToken) {
        switch (lexer.getCurrentToken()) {
            case VOID:
                return Type.VOID;
            case INT:
                return Type.INTEGER;
            case LONG:
                return Type.LONG;
            case FLOAT:
                return Type.FLOAT;
            case DOUBLE:
                return Type.DOUBLE;
            case BOOL:
                return Type.BOOL;
            default:
                throw new RuntimeException("Formal param getType cannot be " + lexer.getCurrentToken());
        }
    }

    public Expression parseExpression() {
        Expression expression = parseRelationalExpression();
        Token token = lexer.getCurrentToken();
        while (Token.ANDAND == token || Token.OR == token) {
            Expression rightExp = parseRelationalExpression();
            expression = new LogicalExpression(expression, rightExp, token);
            token = lexer.getCurrentToken();
        }
        return expression;
    }

    public Expression parseRelationalExpression() {
        Expression expression = parseArithmeticExpression();
        Token token = lexer.getCurrentToken();
        while (Token.DEQ == token || Token.LT == token
                || Token.LTE == token || Token.GT == token
                || Token.GTE == token) {
            Expression rightExp = parseArithmeticExpression();
            expression = new RelationalExpression(expression, rightExp, token);
            token = lexer.getCurrentToken();
        }
        return expression;
    }

    public Expression parseArithmeticExpression() {
        Expression expression = parseTerm();
        Token token = lexer.getCurrentToken();
        while (Token.ADD == token || Token.SUB == token) {
            Expression rightExp = parseTerm();
            expression = new ArithmeticExpressionExpression(expression, rightExp, token);
            token = lexer.getCurrentToken();
        }
        return expression;
    }

    public Expression parseTerm() {
        Expression expression = parseFactor();
        lexer.eat();
        Token token = lexer.getCurrentToken();
        while (Token.MUL == token || Token.DIV == token) {
            Expression rightExp = parseFactor();
            expression = new ArithmeticExpressionExpression(expression, rightExp, token);
            lexer.eat();
            token = lexer.getCurrentToken();
        }
        return expression;
    }

    public Expression parseFactor() {
        lexer.eat();
        Token token = lexer.getCurrentToken();

        switch (token) {
            case NUM:
                if(lexer.getNumType() == Type.DOUBLE) {
                    return new NumericExpression(lexer.getDoubleNum());
                } else if(lexer.getNumType() == Type.FLOAT) {
                    return new NumericExpression(lexer.getFloatNum());
                } else if(lexer.getNumType() == Type.LONG) {
                    return new NumericExpression(lexer.getLongNum());
                } else if(lexer.getNumType() == Type.INTEGER) {
                    return new NumericExpression(lexer.getIntegerNum());
                } else {
                    throw new RuntimeException("Unsupported Data getType");
                }
            case ADD:
                return parseFactor();
            case SUB:
                Expression leftExp = parseFactor();
                return new UnaryExpression(leftExp,
                        lexer.getPreviousToken());
            case OPAR:
                Expression expression = parseExpression();
                lexer.expect(Token.CPAR);
                return expression;
            case STRLTRL:
                return new StringLiteral(lexer.getStringLiteral());
            case VAR_NAME:
                return new VariableExpression(lexer.getVariableName());
            case TRUE:
                return new BooleanExpression(true);
            case FALSE:
                return new BooleanExpression(false);
            case NOT:
                Expression notExp = parseFactor();
                return new NotExpression(notExp);

            default:
                throw new RuntimeException("Unexpected token at leaf : " + token);
        }
    }
}
