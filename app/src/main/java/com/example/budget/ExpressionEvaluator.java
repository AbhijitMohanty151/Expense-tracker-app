package com.example.budget;

/**
 * Evaluates simple arithmetic expressions like "100+30+40-20-50".
 * Supports +, -, *, / and parentheses. Returns null on invalid input.
 */
public class ExpressionEvaluator {

    private String expr;
    private int    pos;

    private ExpressionEvaluator(String expr) {
        // Strip all whitespace
        this.expr = expr.replaceAll("\\s+", "");
        this.pos  = 0;
    }

    /**
     * @return computed double, or null if the expression is invalid / empty.
     */
    public static Double evaluate(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        try {
            ExpressionEvaluator ev = new ExpressionEvaluator(input.trim());
            double result = ev.parseExpression();
            if (ev.pos != ev.expr.length()) return null; // trailing garbage
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // expression = term (('+' | '-') term)*
    private double parseExpression() {
        double result = parseTerm();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '+') { pos++; result += parseTerm(); }
            else if (c == '-') { pos++; result -= parseTerm(); }
            else break;
        }
        return result;
    }

    // term = factor (('*' | '/') factor)*
    private double parseTerm() {
        double result = parseFactor();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '*') { pos++; result *= parseFactor(); }
            else if (c == '/') {
                pos++;
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("Division by zero");
                result /= d;
            } else break;
        }
        return result;
    }

    // factor = ['-'] ( number | '(' expression ')' )
    private double parseFactor() {
        if (pos >= expr.length()) throw new IllegalArgumentException("Unexpected end");

        // Unary minus
        if (expr.charAt(pos) == '-') {
            pos++;
            return -parseFactor();
        }
        // Unary plus
        if (expr.charAt(pos) == '+') {
            pos++;
            return parseFactor();
        }

        // Parenthesised sub-expression
        if (expr.charAt(pos) == '(') {
            pos++; // consume '('
            double result = parseExpression();
            if (pos >= expr.length() || expr.charAt(pos) != ')')
                throw new IllegalArgumentException("Missing closing parenthesis");
            pos++; // consume ')'
            return result;
        }

        // Number
        int start = pos;
        while (pos < expr.length() &&
                (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) throw new IllegalArgumentException("Expected number at " + pos);
        return Double.parseDouble(expr.substring(start, pos));
    }
}
