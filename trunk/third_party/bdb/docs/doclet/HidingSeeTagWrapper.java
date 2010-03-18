/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: HidingSeeTagWrapper.java,v 1.5 2010/01/04 15:50:33 cwl Exp $
 */

import java.util.Map;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.SeeTag;

class HidingSeeTagWrapper extends HidingTagWrapper implements SeeTag {
    public HidingSeeTagWrapper(SeeTag seetag, Map mapWrappers) {
        super(seetag, mapWrappers);
    }

    private SeeTag _getSeeTag() {
        return (SeeTag)getWrappedObject();
    }

    public String label() {
        return _getSeeTag().label();
    }

    public ClassDoc referencedClass() {
        return (ClassDoc)wrapOrHide(_getSeeTag().referencedClass());
    }

    public String referencedClassName() {
        return _getSeeTag().referencedClassName();
    }

    public MemberDoc referencedMember() {
        return (MemberDoc)wrapOrHide(_getSeeTag().referencedMember());
    }

    public String referencedMemberName() {
        return _getSeeTag().referencedMemberName();
    }

    public PackageDoc referencedPackage() {
        return (PackageDoc)wrapOrHide(_getSeeTag().referencedPackage());
    }
}
