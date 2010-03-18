############################################################################
#                                                                          #
#                                                                          #
#  Copyright (c) 2005 by Timothy W Macinta, All Rights Reserved.           #
#                                                                          #
#  This file can be used to compile the Fast MD5 distribution on Linux.    #
#                                                                          #
#  This library is free software; you can redistribute it and/or           #
#  modify it under the terms of the GNU Library General Public             #
#  License as published by the Free Software Foundation; either            #
#  version 2.1 of the License, or (at your option) any later version.      #
#                                                                          #
#  This library is distributed in the hope that it will be useful,         #
#  but WITHOUT ANY WARRANTY; without even the implied warranty of          #
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU       #
#  Library General Public License for more details.                        #
#                                                                          #
#  You should have received a copy of the GNU Library General Public       #
#  License along with this library; if not, write to the Free              #
#  Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.      #
#                                                                          #
#  See http://www.twmacinta.com/myjava/fast_md5.php for more information   #
#  on this package.                                                        #
#                                                                          #
############################################################################

# change this to match your system
JAVADIR=/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home/


JAVABINDIR=$(JAVADIR)bin/
JAVAC=$(JAVABINDIR)javac
JAVAH=$(JAVABINDIR)javah

CPPFLAGS=-I. -g

all: all_objs

clean:
	rm -f $(call toShellArg,$(ALL_OBJS))


ALL_OBJS= \
	$(ALL_CLASSES) \
	lib/arch/darwin_x86/MD5.jnilib \
	com/twmacinta/util/MD5.h \

#	lib/arch/linux_x86/MD5.so \

ALL_CLASSES= \
	com/twmacinta/io/NullOutputStream.class \
	com/twmacinta/util/MD5.class \
	com/twmacinta/util/MD5InputStream.class \
	com/twmacinta/util/MD5OutputStream.class \
	com/twmacinta/util/MD5State.class \
	com/twmacinta/util/test/MD5OutputStreamTest.class \

com/twmacinta/util/MD5.h: com/twmacinta/util/MD5.class
	$(JAVABINDIR)javah -force -o com/twmacinta/util/MD5.h com.twmacinta.util.MD5

lib/arch/linux_x86/MD5.so: com/twmacinta/util/MD5.c com/twmacinta/util/MD5.h
	mkdir -p lib/arch/linux_x86
	gcc -O3 -shared -I$(JAVADIR)include -I$(JAVADIR)include/linux com/twmacinta/util/MD5.c -o lib/arch/linux_x86/MD5.so

lib/arch/darwin_x86/MD5.jnilib: com/twmacinta/util/MD5.c com/twmacinta/util/MD5.h
	mkdir -p lib/arch/darwin_x86
	gcc -O3 -bundle -I/System/Library/Frameworks/JavaVM.framework/Headers -o lib/arch/darwin_x86/MD5.jnilib -framework JavaVM com/twmacinta/util/MD5.c

all_objs: $(ALL_OBJS)


############################################################################
############################################################################
##
##    Rules for generating class files
##

# This strips off the inner class portion.  Yuck.
toJavaSrc=$(word 1,$(subst $$, ,$(patsubst %.class,%,$(1)))).java

# This converts the path to a classfile to a fully qualified classname
pathToClassname=$(subst /,.,$(patsubst %.class,%,$(1)))

# This converts inner class names to an appropriate format for shell calls
toShellArg=$(subst $$,\$$,$(1))

# Trick Make into thinking we know how to create .java files automatically
%.java:
	test -f $(call toJavaSrc,$@)

# The rule for building class files
%.class: %.java
	$(JAVAC) $(call toJavaSrc,$@)

##
##
############################################################################
############################################################################
