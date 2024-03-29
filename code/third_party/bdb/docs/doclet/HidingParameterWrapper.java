/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: HidingParameterWrapper.java,v 1.5 2010/01/04 15:50:33 cwl Exp $
 */

import java.util.Map;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Type;

class HidingParameterWrapper extends HidingWrapper implements Parameter {
    public HidingParameterWrapper(Parameter param, Map mapWrappers) {
        super(param, mapWrappers);
    }

    private Parameter _getParameter() {
        return (Parameter)getWrappedObject();
    }

    public String name() {
        return _getParameter().name();
    }

    public String toString() {
        return _getParameter().toString();
    }

    public Type type() {
        return (Type)wrapOrHide(_getParameter().type());
    }

    public String typeName() {
        return _getParameter().typeName();
    }

    public AnnotationDesc[] annotations() {
        return (AnnotationDesc[])wrapOrHide(_getParameter().annotations());
    }
}
