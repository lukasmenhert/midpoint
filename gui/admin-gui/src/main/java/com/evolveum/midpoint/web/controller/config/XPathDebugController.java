/*
 * Copyright (c) 2011 Evolveum
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
 * Portions Copyrighted 2011 [name of copyright owner]
 * Portions Copyrighted 2010 Forgerock
 */
package com.evolveum.midpoint.web.controller.config;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.xml.PrismJaxbProcessor;
import com.evolveum.midpoint.schema.holder.ExpressionCodeHolder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.bean.BrowserBean;
import com.evolveum.midpoint.web.bean.XPathVariableBean;
import com.evolveum.midpoint.web.controller.util.ControllerUtil;
import com.evolveum.midpoint.web.util.FacesUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Katuska
 */
@Controller("debugXPath")
@Scope("session")
public class XPathDebugController implements Serializable {

	public static final String PAGE_NAVIGATION_XPATH_DEBUG = "/admin/config/debugXPath?faces-redirect=true";
	private static final long serialVersionUID = 7295076387943631763L;
	private static final String PARAM_VARIABLE_NAME = "variableName";
	private static final Trace LOGGER = TraceManager.getTrace(XPathDebugController.class);
	private static final List<SelectItem> returnTypes = new ArrayList<SelectItem>();
	private static final List<SelectItem> types = new ArrayList<SelectItem>();
	static {
		returnTypes.add(new SelectItem("String"));
		returnTypes.add(new SelectItem("Number"));
		returnTypes.add(new SelectItem("Node"));
		returnTypes.add(new SelectItem("NodeList"));
		returnTypes.add(new SelectItem("Boolean"));
		returnTypes.add(new SelectItem("DomObjectModel"));

		types.add(new SelectItem("Object"));
		types.add(new SelectItem("String"));
	}
	@Autowired
	private transient ModelService modelService;
    @Autowired(required = true)
    private transient PrismContext prismContext;
	private String expression;
	private List<XPathVariableBean> variables = new ArrayList<XPathVariableBean>();
	private boolean selectAll;
	private String returnType;
	private String result;
	// browsing
	private String variableName;
	private boolean showBrowser;
	private BrowserBean<XPathVariableBean> browser;

	public String cleanupController() {
		expression = null;
		variables = null;
		selectAll = false;
		returnType = null;
		result = null;

		return PAGE_NAVIGATION_XPATH_DEBUG;
	}

	private ExpressionCodeHolder getExpressionHolderFromExpresion() {
		LOGGER.debug("getExpressionHolder start");

		Document doc = DOMUtil.getDocument();
		Element element = doc.createElement("valueExpresion");
		element.setTextContent(expression);
		ExpressionCodeHolder expressionHolder = new ExpressionCodeHolder(element);
		LOGGER.debug("expression holder: {}", expressionHolder.getFullExpressionAsString());
		LOGGER.debug("getExpressionHolder end");
		return expressionHolder;
	}

	private QName getQNameForVariable(String variable) {
		LOGGER.debug("getQNameForVariable start");
		ExpressionCodeHolder expressionHolder = getExpressionHolderFromExpresion();
		Map<String, String> namespaceMap = expressionHolder.getNamespaceMap();

		if (variable.contains(":")) {
			String[] variableNS = variable.split(":");
			String namespace = namespaceMap.get(variableNS[0]);
			return new QName(namespace, variableNS[1]);
		} else {
			QName qname = new QName(variable);
			return qname;
		}
	}

	private Map<QName, Object> getVariableValue() throws JAXBException {
		LOGGER.debug("getVariableValue start");
		Map<QName, Object> variableMap = new HashMap<QName, Object>();
		for (XPathVariableBean variable : getVariables()) {
			if (StringUtils.isNotEmpty(variable.getVariableName())) {
				if (variable.getType().equals("Object")) {
					try {
						PrismObject<ObjectType> objectType = modelService.getObject(ObjectType.class, variable.getValue(),
								new PropertyReferenceListType(), new OperationResult("Get object"));
						// Variable only accepts String or Node, but here we
						// will get a JAXB object. Need to convert it.
                        PrismJaxbProcessor jaxbProcessor = prismContext.getPrismJaxbProcessor();
                        Document document = DOMUtil.getDocument(); 
                        jaxbProcessor.marshalToDom(objectType.asObjectable(), document);
						Element jaxbToDom = document.getDocumentElement(); //JAXBUtil.jaxbToDom(objectType, SchemaConstants.I_OBJECT, null);
						// TODO: May need to add xsi:type attribute here
						variableMap.put(getQNameForVariable(variable.getVariableName()), 
								jaxbToDom);
					} catch (Exception ex) {
						LoggingUtils.logException(LOGGER, "Failed to get variable value {}", ex,
								variable.getValue());
					}
				}
				if (variable.getType().equals("String")) {
					variableMap.put(getQNameForVariable(variable.getVariableName()),
							variable.getValue());
				}
			}
		}
		// LOGGER.info("variable value {}",
		// variableMap.get(QNameUtil.uriToQName("http://xxx.com/")));
		// LOGGER.info("getVariableValue end");
		return variableMap;
	}

	public String evaluate() {
		LOGGER.debug("evaluate start");
		if (expression == null || expression.isEmpty()) {
			FacesUtils.addErrorMessage("Expresion cannot be null.");
			return null;
		}

// todo commented out during refactoring, will be fixed later
//		try {
//			ExpressionCodeHolder expressionHolder = getExpressionHolderFromExpresion();
//			if (returnType.equals("Boolean")) {
//				Boolean boolResult = (Boolean) XPathUtil.evaluateExpression(getVariableValue(),
//						expressionHolder, XPathConstants.BOOLEAN);
//				result = String.valueOf(boolResult);
//			}
//			if (returnType.equals("Number")) {
//				Double doubleResult = (Double) XPathUtil.evaluateExpression(getVariableValue(),
//						expressionHolder, XPathConstants.NUMBER);
//				result = String.valueOf(doubleResult);
//			}
//			if (returnType.equals("String") || returnType.equals("DomObjectModel")) {
//				result = (String) XPathUtil.evaluateExpression(getVariableValue(), expressionHolder,
//						XPathConstants.STRING);
//			}
//
//			if (returnType.equals("Node")) {
//				Node nodeResult = (Node) XPathUtil.evaluateExpression(getVariableValue(), expressionHolder,
//						XPathConstants.NODE);
//				result = DOMUtil.printDom(nodeResult).toString();
//			}
//			if (returnType.equals("NodeList")) {
//				NodeList nodeListResult = (NodeList) XPathUtil.evaluateExpression(getVariableValue(),
//						expressionHolder, XPathConstants.NODESET);
//				StringBuffer strBuilder = new StringBuffer();
//				for (int i = 0; i < nodeListResult.getLength(); i++) {
//					strBuilder.append(DOMUtil.printDom(nodeListResult.item(i)));
//				}
//				result = strBuilder.toString();
//			}
//		} catch (JAXBException ex) {
//			FacesUtils.addErrorMessage("JAXB error occured, reason: " + ex.getMessage(), ex);
//		}

		LOGGER.debug("result is: {}", result);
		LOGGER.debug("evaluate end");
		return null;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public List<SelectItem> getTypes() {
		return types;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public List<XPathVariableBean> getVariables() {
		if (variables == null) {
			variables = new ArrayList<XPathVariableBean>();
			addVariablePerformed();
		}
		return variables;
	}

	public List<SelectItem> getReturnTypes() {
		return returnTypes;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public void addVariablePerformed() {
		XPathVariableBean variable = new XPathVariableBean();
		variable.setType("String");

		getVariables().add(variable);
	}

	public void deleteVariablesPerformed() {
		List<XPathVariableBean> toDelete = new ArrayList<XPathVariableBean>();
		for (XPathVariableBean bean : getVariables()) {
			if (bean.isSelected()) {
				toDelete.add(bean);
			}
		}

		getVariables().removeAll(toDelete);
	}

	public boolean isSelectAll() {
		return selectAll;
	}

	public void setSelectAll(boolean selectAll) {
		this.selectAll = selectAll;
	}

	public void selectPerformed(ValueChangeEvent evt) {
		this.selectAll = ControllerUtil.selectPerformed(evt, getVariables());
	}

	public void selectAllPerformed(ValueChangeEvent evt) {
		ControllerUtil.selectAllPerformed(evt, getVariables());
	}

	public BrowserBean<XPathVariableBean> getBrowser() {
		if (browser == null) {
			browser = new BrowserBean<XPathVariableBean>();
			browser.setModel(modelService);
		}
		return browser;
	}

	public boolean isShowBrowser() {
		return showBrowser;
	}

	public void browse() {
		variableName = FacesUtils.getRequestParameter(PARAM_VARIABLE_NAME);
		if (StringUtils.isEmpty(variableName)) {
			FacesUtils.addErrorMessage("Variable name not defined.");
			return;
		}

		browser.setModel(modelService);
		showBrowser = true;
	}

	public String okAction() {
		if (StringUtils.isEmpty(variableName)) {
			FacesUtils.addErrorMessage("Variable name not defined.");
			return null;
		}
		String objectOid = FacesUtils.getRequestParameter(BrowserBean.PARAM_OBJECT_OID);
		if (StringUtils.isEmpty(objectOid)) {
			FacesUtils.addErrorMessage("Object oid not defined.");
			return null;
		}

		for (XPathVariableBean bean : getVariables()) {
			if (variableName.equals(bean.getVariableName())) {
				bean.setValue(objectOid);
			}
		}
		browseCleanup();

		return null;
	}

	private void browseCleanup() {
		variableName = null;
		showBrowser = false;
		browser.cleanup();
	}

	public String cancelAction() {
		browseCleanup();

		return null;
	}
}
