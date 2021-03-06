package com.slang.ast;

import com.slang.SymbolInfo;
import com.slang.visitor.Context;
import com.slang.visitor.IVisitor;

/**
 * Created by sarath on 16/3/17.
 */
public class ArithmeticExpressionExpression extends Expression {

    private Expression leftExpression;
    private Expression rightExpression;
    private Token operator;

    public ArithmeticExpressionExpression(Expression leftExpression, Expression rightExpression, Token operator) {
        this.leftExpression = leftExpression;
        this.rightExpression = rightExpression;
        this.operator = operator;
    }

    public Expression getLeftExpression() {
        return leftExpression;
    }

    public Expression getRightExpression() {
        return rightExpression;
    }

    public Token getOperator() {
        return operator;
    }

    public SymbolInfo accept(IVisitor visitor, Context context) {
        return visitor.visit(this, context);
    }

    @Override
    public String toString() {
        return "ArithmeticExpressionExpression{" +
                "leftExpression=" + leftExpression +
                ", rightExpression=" + rightExpression +
                ", operator=" + operator +
                '}';
    }
}
