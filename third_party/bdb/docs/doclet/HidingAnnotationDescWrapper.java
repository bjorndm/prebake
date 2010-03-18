/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: HidingAnnotationDescWrapper.java,v 1.6 2010/01/04 15:50:33 cwl Exp $
 */

import java.util.Map;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeDoc;

class HidingAnnotationDescWrapper extends HidingWrapper
                                  implements AnnotationDesc {
    
    public HidingAnnotationDescWrapper(AnnotationDesc type, 
                                       Map mapWrappers) {
        super(type, mapWrappers);
    }

    private AnnotationDesc _getAnnotationDesc() {
        return (AnnotationDesc)getWrappedObject();
    }

    public AnnotationTypeDoc annotationType() {
        return (AnnotationTypeDoc)
                wrapOrHide(_getAnnotationDesc().annotationType());
    }

    public AnnotationDesc.ElementValuePair[] elementValues() {
        return _getAnnotationDesc().elementValues();
    }
}
