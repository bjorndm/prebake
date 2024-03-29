/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: HidingAnnotationValueWrapper.java,v 1.6 2010/01/04 15:50:33 cwl Exp $
 */

import java.util.Map;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeElementDoc;
import com.sun.javadoc.AnnotationValue;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Tag;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;

class HidingAnnotationValueWrapper extends HidingWrapper 
                                   implements AnnotationValue {
    public HidingAnnotationValueWrapper(AnnotationValue value, 
                                            Map mapWrappers) {
        super(value, mapWrappers);
    }

    public AnnotationValue _getAnnotationValue() {
        return (AnnotationValue)getWrappedObject();
    }

    public Object value() {
        return _getAnnotationValue().value();
    }

    public String toString() {
        return _getAnnotationValue().toString();
    }
}
