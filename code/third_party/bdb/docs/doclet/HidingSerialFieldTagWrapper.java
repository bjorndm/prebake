/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: HidingSerialFieldTagWrapper.java,v 1.6 2010/01/04 15:50:33 cwl Exp $
 */

import java.util.Map;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.SerialFieldTag;

class HidingSerialFieldTagWrapper extends HidingTagWrapper
                                  implements SerialFieldTag {
    public HidingSerialFieldTagWrapper(SerialFieldTag serfldtag, 
                                       Map mapWrappers) {
        super(serfldtag, mapWrappers);
    }

    private SerialFieldTag _getSerialFieldTag() {
        return (SerialFieldTag)getWrappedObject();
    }

    public int compareTo(Object obj) {
        if (obj instanceof HidingWrapper) {
            return _getSerialFieldTag().
                   compareTo(((HidingWrapper)obj).getWrappedObject());
        } else {
            return _getSerialFieldTag().compareTo(obj);
        }
    }

    public String description() {
        return _getSerialFieldTag().description();
    }

    public String fieldName() {
        return _getSerialFieldTag().fieldName();
    }

    public String fieldType() {
        return _getSerialFieldTag().fieldType();
    }

    public ClassDoc fieldTypeDoc() {
        return (ClassDoc)wrapOrHide(_getSerialFieldTag().fieldTypeDoc());
    }
}
