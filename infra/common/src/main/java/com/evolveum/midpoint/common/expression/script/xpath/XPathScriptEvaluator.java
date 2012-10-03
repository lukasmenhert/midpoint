/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */
package com.evolveum.midpoint.common.expression.script.xpath;

import com.evolveum.midpoint.common.expression.ExpressionSyntaxException;
import com.evolveum.midpoint.common.expression.MidPointFunctions;
import com.evolveum.midpoint.common.expression.script.ScriptEvaluator;
import com.evolveum.midpoint.common.expression.script.ScriptVariables;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.prism.xml.XsdTypeMapper;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ExceptionUtil;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_2.ScriptExpressionEvaluatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2.ScriptExpressionReturnTypeType;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Radovan Semancik
 */
public class XPathScriptEvaluator implements ScriptEvaluator {

    public static String XPATH_LANGUAGE_URL = "http://www.w3.org/TR/xpath/";

    private XPathFactory factory = XPathFactory.newInstance();
    
    private PrismContext prismContext;

    public XPathScriptEvaluator(PrismContext prismContext) {
		this.prismContext = prismContext;
	}

    @Override
	public <T> List<PrismPropertyValue<T>> evaluate(ScriptExpressionEvaluatorType expressionType,
			ScriptVariables variables, ItemDefinition outputDefinition, ScriptExpressionReturnTypeType suggestedReturnType, 
			ObjectResolver objectResolver, MidPointFunctions functionLibrary,
			String contextDescription, OperationResult result) throws ExpressionEvaluationException,
			ObjectNotFoundException, ExpressionSyntaxException {

    	Element codeElement = expressionType.getCode();
		if (codeElement == null) {
			throw new ExpressionEvaluationException("No script code in " + contextDescription);
		}
		
		QName xsdReturnType = outputDefinition.getTypeName();
        Class<T> type = XsdTypeMapper.toJavaType(xsdReturnType);
		
        QName returnType = determineRerturnType(type, expressionType, outputDefinition, suggestedReturnType);

        Object evaluatedExpression = evaluate(returnType, codeElement, variables, objectResolver, functionLibrary, contextDescription, result);

        List<PrismPropertyValue<T>> propertyValues;
        
        boolean scalar = !outputDefinition.isMultiValue();
        if (expressionType.getReturnType() != null) {
        	scalar = isScalar(expressionType.getReturnType());
        } else if (suggestedReturnType != null) {
        	scalar = isScalar(suggestedReturnType);
        }
        
        if (scalar) {
        	if (evaluatedExpression instanceof NodeList) {
        		NodeList evaluatedExpressionNodeList = (NodeList)evaluatedExpression;
        		if (evaluatedExpressionNodeList.getLength() > 1) {
        			throw new ExpressionEvaluationException("Expected scalar expression result but got a list result with "+evaluatedExpressionNodeList.getLength()+" elements in "+contextDescription);
        		}
        		if (evaluatedExpressionNodeList.getLength() == 0) {
        			evaluatedExpression = null;
        		} else {
        			evaluatedExpression = evaluatedExpressionNodeList.item(0);
        		}
        	}
        	propertyValues = new ArrayList<PrismPropertyValue<T>>(1);
        	PrismPropertyValue<T> pval = convertScalar(type, returnType, evaluatedExpression, contextDescription);
        	if (!isNothing(pval.getValue())) {
        		propertyValues.add(pval);
        	}
        } else {
        	if (!(evaluatedExpression instanceof NodeList)) {
                throw new IllegalStateException("The expression " + contextDescription + " resulted in " + evaluatedExpression.getClass().getName() + " while exprecting NodeList in "+contextDescription);
            }
        	propertyValues = convertList(type, (NodeList) evaluatedExpression, contextDescription);
        }
        
        return propertyValues;
    }

	private boolean isScalar(ScriptExpressionReturnTypeType returnType) {
		if (returnType == ScriptExpressionReturnTypeType.SCALAR) {
    		return true;
    	} else {
    		return false;
    	}
	}

	private Object evaluate(QName returnType, Element code, ScriptVariables variables, ObjectResolver objectResolver,
    		MidPointFunctions functionLibrary, String contextDescription, OperationResult result)
            throws ExpressionEvaluationException, ObjectNotFoundException, ExpressionSyntaxException {

        XPathExpressionCodeHolder codeHolder = new XPathExpressionCodeHolder(code);

        XPath xpath = factory.newXPath();
        XPathVariableResolver variableResolver = new LazyXPathVariableResolver(variables, objectResolver, contextDescription, result);
        xpath.setXPathVariableResolver(variableResolver);
        xpath.setNamespaceContext(new MidPointNamespaceContext(codeHolder.getNamespaceMap()));
        xpath.setXPathFunctionResolver(getFunctionResolver(functionLibrary));

        XPathExpression expr;
        try {

            expr = xpath.compile(codeHolder.getExpressionAsString());

        } catch (Exception e) {
            Throwable originalException = ExceptionUtil.lookForTunneledException(e);
            if (originalException != null && originalException instanceof ObjectNotFoundException) {
                throw (ObjectNotFoundException) originalException;
            }
            if (originalException != null && originalException instanceof ExpressionSyntaxException) {
                throw (ExpressionSyntaxException) originalException;
            }
            if (e instanceof XPathExpressionException) {
                throw createExpressionEvaluationException(e, contextDescription);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new SystemException(e.getMessage(), e);
        }

        Object rootNode;
		try {
			rootNode = determineRootNode(variableResolver, contextDescription);
		} catch (SchemaException e) {
			throw new ExpressionSyntaxException(e.getMessage(), e);
		}
        Object evaluatedExpression;

        try {

            evaluatedExpression = expr.evaluate(rootNode, returnType);

        } catch (Exception e) {
            Throwable originalException = ExceptionUtil.lookForTunneledException(e);
            if (originalException != null && originalException instanceof ObjectNotFoundException) {
                throw (ObjectNotFoundException) originalException;
            }
            if (originalException != null && originalException instanceof ExpressionSyntaxException) {
                throw (ExpressionSyntaxException) originalException;
            }
            if (e instanceof XPathExpressionException) {
                throw createExpressionEvaluationException(e, contextDescription);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new SystemException(e.getMessage(), e);
        }

        if (evaluatedExpression == null) {
            return null;
        }

        return evaluatedExpression;
    }


    private ExpressionEvaluationException createExpressionEvaluationException(Exception e, String contextDescription) {
        return new ExpressionEvaluationException(lookForMessage(e) + " in " + contextDescription, e);
    }
    
    public static String lookForMessage(Throwable e) {
    	// the net.sf.saxon.trans.XPathException lies. It has meaningless message. skip it.
    	if (e instanceof net.sf.saxon.trans.XPathException && e.getCause() != null) {
    		return lookForMessage(e.getCause());
    	}
		if (e.getMessage() != null) {
			return e.getMessage();
		}
		if (e.getCause() != null) {
			return lookForMessage(e.getCause());
		}
		return null;
	}

    /**
     * Kind of convenience magic. Try few obvious variables and set them as the root node
     * for evaluation. This allow to use "fullName" instead of "$user/fullName".
     */
    private Object determineRootNode(XPathVariableResolver variableResolver, String contextDescription) throws SchemaException {
        Object rootNode = variableResolver.resolveVariable(null);
        if (rootNode == null) {
        	// Add empty document instead of null so the expressions don't die with exception.
        	// This is necessary e.g. on deletes in sync when there may be nothing to evaluate.
        	return DOMUtil.getDocument();
        } else {
        	return LazyXPathVariableResolver.convertToXml(rootNode, null, contextDescription);
        }
    }

	private <T> QName determineRerturnType(Class<T> type, ScriptExpressionEvaluatorType expressionType,
			ItemDefinition outputDefinition, ScriptExpressionReturnTypeType suggestedReturnType) throws ExpressionEvaluationException {

		if (expressionType.getReturnType() == ScriptExpressionReturnTypeType.LIST || suggestedReturnType == ScriptExpressionReturnTypeType.LIST) {
			return XPathConstants.NODESET;
		}
		
		if (expressionType.getReturnType() == ScriptExpressionReturnTypeType.SCALAR) {
			return toXPathReturnType(outputDefinition.getTypeName());
		}
		
		if (suggestedReturnType == ScriptExpressionReturnTypeType.LIST) {
			return XPathConstants.NODESET;
		}
		
		if (suggestedReturnType == ScriptExpressionReturnTypeType.SCALAR) {
			return toXPathReturnType(outputDefinition.getTypeName());
		}
		
		if (outputDefinition.isMultiValue()) {
			return XPathConstants.NODESET;
		} else {
			return toXPathReturnType(outputDefinition.getTypeName());
		}
	}

	private QName toXPathReturnType(QName xsdTypeName) throws ExpressionEvaluationException {
		if (xsdTypeName.equals(DOMUtil.XSD_STRING)) {
			return XPathConstants.STRING;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_FLOAT)) {
			return XPathConstants.NUMBER;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_DOUBLE)) {
			return XPathConstants.NUMBER;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_INT)) {
			return XPathConstants.NUMBER;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_INTEGER)) {
			return XPathConstants.NUMBER;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_LONG)) {
			return XPathConstants.NUMBER;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_BOOLEAN)) {
			return XPathConstants.BOOLEAN;
		}
		if (xsdTypeName.equals(DOMUtil.XSD_DATETIME)) {
			return XPathConstants.STRING;
		}
		if (xsdTypeName.equals(PolyStringType.COMPLEX_TYPE)) {
			return XPathConstants.STRING;
		}
		throw new ExpressionEvaluationException("Unsupported return type " + xsdTypeName);
	}


	/*
	 if (type.equals(String.class))
		{
            return XPathConstants.STRING;
        }
        if (type.equals(Double.class) || type.equals(double.class)) {
            return XPathConstants.NUMBER;
        }
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return XPathConstants.NUMBER;
        }
        if (type.equals(Long.class) || type.equals(long.class)) {
            return XPathConstants.NUMBER;
        }
        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return XPathConstants.BOOLEAN;
        }
        if (type.equals(NodeList.class)) {
        	if (expressionType.getReturnType() == ScriptExpressionReturnTypeType.SCALAR) {
        		// FIXME: is this OK?
        		return XPathConstants.STRING;
        	} else {
        		return XPathConstants.NODESET;
        	}
        }
        if (type.equals(Node.class)) {
            return XPathConstants.NODE;
        }
        if (type.equals(PolyString.class) || type.equals(PolyStringType.class)) {
        	return XPathConstants.STRING;
        }
        throw new ExpressionEvaluationException("Unsupported return type " + type);
    }
*/
    private <T> PrismPropertyValue<T> convertScalar(Class<T> type, QName returnType, Object value,
            String contextDescription) throws ExpressionEvaluationException {
        if (type.isAssignableFrom(value.getClass())) {
            return new PrismPropertyValue<T>((T) value);
        }
        try {
        	T resultValue = null;
            if (value instanceof String) {
            	resultValue = XmlTypeConverter.toJavaValue((String) value, type);
            } else if (value instanceof Boolean) {
            	resultValue = (T)value;
            } else if (value instanceof Element) {
            	resultValue = XmlTypeConverter.convertValueElementAsScalar((Element) value, type);
            } else {
            	throw new ExpressionEvaluationException("Unexpected scalar return type " + value.getClass().getName());
            }
            if (returnType.equals(PrismConstants.POLYSTRING_TYPE_QNAME) && resultValue instanceof String) {
            	resultValue = (T) new PolyString((String)resultValue);
            }
            if (resultValue instanceof PolyString) {
            	((PolyString)resultValue).recompute(prismContext.getDefaultPolyStringNormalizer());
            }
            
            return new PrismPropertyValue<T>(resultValue);
        } catch (SchemaException e) {
            throw new ExpressionEvaluationException("Error converting result of "
                    + contextDescription + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ExpressionEvaluationException("Error converting result of "
                    + contextDescription + ": " + e.getMessage(), e);
        }
    }

    private <T> List<PrismPropertyValue<T>> convertList(Class<T> type, NodeList valueNodes, String contextDescription) throws
            ExpressionEvaluationException {
        List<PrismPropertyValue<T>> values = new ArrayList<PrismPropertyValue<T>>();
        if (valueNodes == null) {
            return values;
        }

        try {
            List<T> list = XmlTypeConverter.convertValueElementAsList(valueNodes, type);
            for (T item : list) {
                if (isNothing(item)) {
                    continue;
                }
                values.add(new PrismPropertyValue<T>(item));
            }
            return values;
        } catch (SchemaException e) {
            throw new ExpressionEvaluationException("Error converting return value of " + contextDescription + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ExpressionEvaluationException("Error converting return value of " + contextDescription + ": " + e.getMessage(), e);
        }
    }
    
    private <T> boolean isNothing(T value) {
    	return value == null || ((value instanceof String) && ((String) value).isEmpty());
    }

    private XPathFunctionResolver getFunctionResolver(MidPointFunctions functionLibrary) {
    	return new ReflectionXPathFunctionResolver(functionLibrary);
    }

    @Override
    public String getLanguageName() {
        return "XPath 2.0";
    }
    
	@Override
	public String getLanguageUrl() {
		return XPATH_LANGUAGE_URL;
	}

}
