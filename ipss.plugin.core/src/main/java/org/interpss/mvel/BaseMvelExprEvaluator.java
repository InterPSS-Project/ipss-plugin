package org.interpss.mvel;

import java.util.HashMap;
import java.util.Map;

import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import com.interpss.common.exp.InterpssException;


/**
 * Based MEVL expression evaluator implementation.
 *
 */
public abstract class BaseMvelExprEvaluator {
	// context for the MVEL expression evaluation
	protected Map<String, Object> context;
	
	// MVEL function factory 
	protected VariableResolverFactory varsFactory;

	/**
	 * Constructor
	 * 
	 */
	public BaseMvelExprEvaluator() {
		this.context = new HashMap<>();
		this.context.put("func", this);
		this.context.put("math", Math.class);
		this.varsFactory = new MapVariableResolverFactory(this.context);
	}
	
	/**
	 * evaluate the expression
	 * 
	 * @param <T>
	 * @param expr
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T evalMvelExpression(String expr) throws InterpssException {
		try {
			Object result = MVEL.eval(expr, this.varsFactory);
			return (T) result;
		} catch (Exception e) {
			throw new InterpssException(e.toString());
		}
	}
	
	/**
	 * evaluate the expression with the variable map
	 * 
	 * @param <T>
	 * @param expr
	 * @param vars expression variable map
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T evalMvelExpression(String expr, Map<String, Object> vars) throws InterpssException {
		try {
			Object result = MVEL.eval(expr, vars, this.varsFactory);
			return (T) result;
		} catch (Exception e) {
			throw new InterpssException(e.toString());
		}
	}
}
