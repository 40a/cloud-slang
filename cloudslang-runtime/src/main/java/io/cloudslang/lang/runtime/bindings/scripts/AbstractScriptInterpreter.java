/**
 * ****************************************************************************
 * (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 * <p/>
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * *****************************************************************************
 */
package io.cloudslang.lang.runtime.bindings.scripts;

import org.python.core.*;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Bonczidai Levente
 * @since 1/19/2016
 */
public abstract class AbstractScriptInterpreter {

    @Autowired
    private PythonInterpreter interpreter;


    protected void cleanInterpreter() {
        interpreter.setLocals(new PyStringMap());
    }

    protected PyObject getValueFromContext(String name) {
        return interpreter.get(name);
    }

    protected void setValueInContext(String name, Object value) {
        interpreter.set(name, value);
    }

    protected Map<String, Serializable> exec(String script) {
        interpreter.exec(script);
        Iterator<PyObject> localsIterator = getLocals().asIterable().iterator();
        Map<String, Serializable> returnValue = new HashMap<>();
        while (localsIterator.hasNext()) {
            String key = localsIterator.next().asString();
            PyObject value = getValueFromContext(key);
            if (keyIsExcluded(key, value)) {
                continue;
            }
                Serializable javaValue = resolveJythonObjectToJavaExec(value, key);
                returnValue.put(key, javaValue);
        }
        return returnValue;
    }

    protected Serializable eval(String script) {
        PyObject evalResultAsPyObject = interpreter.eval(script);
        Serializable evalResult;
        evalResult = resolveJythonObjectToJavaEval(evalResultAsPyObject, script);
        return evalResult;
    }

    protected PyObject getLocals() {
        return interpreter.getLocals();
    }

    private Serializable resolveJythonObjectToJavaExec(PyObject value, String key) {
        String errorMessage =
                "Non-serializable values are not allowed in the output context of a Python script:\n" +
                        "\tConversion failed for '" + key + "' (" + String.valueOf(value) + "),\n" +
                        "\tThe error can be solved by removing the variable from the context in the script: e.g. 'del " + key + "'.\n";
        return resolveJythonObjectToJava(value, errorMessage);
    }

    private Serializable resolveJythonObjectToJavaEval(PyObject value, String expression) {
        String errorMessage =
                "Evaluation result for a Python expression should be serializable:\n" +
                        "\tConversion failed for '" + expression + "' (" + String.valueOf(value) + ").\n";
        return resolveJythonObjectToJava(value, errorMessage);
    }

    private Serializable resolveJythonObjectToJava(PyObject value, String errorMessage) {
        if (value instanceof PyBoolean) {
            PyBoolean pyBoolean = (PyBoolean) value;
            return pyBoolean.getBooleanValue();
        } else {
            try {
                return Py.tojava(value, Serializable.class);
            } catch (PyException e) {
                PyObject typeObject = e.type;
                if (typeObject instanceof PyType) {
                    PyType type = (PyType) typeObject;
                    String typeName = type.getName();
                    if ("TypeError".equals(typeName)) {
                        throw new RuntimeException(errorMessage, e);
                    }
                }
                throw e;
            }
        }
    }

    private boolean keyIsExcluded(String key, PyObject value) {
        return (key.startsWith("__") && key.endsWith("__")) ||
                value instanceof PyFile ||
                value instanceof PyModule ||
                value instanceof PyFunction ||
                value instanceof PySystemState;
    }

}
