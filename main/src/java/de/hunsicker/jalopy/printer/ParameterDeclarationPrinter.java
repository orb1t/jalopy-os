/*
 * Copyright (c) 2001-2002, Marco Hunsicker. All rights reserved.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 */
package de.hunsicker.jalopy.printer;

import java.io.IOException;

import antlr.collections.AST;
import de.hunsicker.jalopy.language.antlr.JavaTokenTypes;


/**
 * Printer for parameter declarations [<code>PARAMETER_DEF</code>].
 *
 * @author <a href="http://jalopy.sf.net/contact.html">Marco Hunsicker</a>
 * @version $Revision$
 */
final class ParameterDeclarationPrinter
    extends AbstractPrinter
{
    //~ Static variables/initializers ----------------------------------------------------

    private static final Printer INSTANCE = new ParameterDeclarationPrinter();

    //~ Constructors ---------------------------------------------------------------------

    /**
     * Creates a new ParameterDeclarationPrinter object.
     */
    public ParameterDeclarationPrinter()
    {
    }

    //~ Methods --------------------------------------------------------------------------

    /**
     * Returns the sole instance of this class.
     *
     * @return the sole instance of this class.
     */
    public static final Printer getInstance()
    {
        return INSTANCE;
    }


    /**
     * {@inheritDoc}
     */
    public void print(
        AST        node,
        NodeWriter out)
      throws IOException
    {
        AST modifier = node.getFirstChild();
        PrinterFactory.create(modifier).print(modifier, out);

        AST type = modifier.getNextSibling();
        PrinterFactory.create(type).print(type, out);

        // align the parameter
        if (
            (out.state.paramOffset != ParametersPrinter.OFFSET_NONE)
            && (out.column < out.state.paramOffset))
        {
            out.print(
                out.getString(out.state.paramOffset - out.column), JavaTokenTypes.WS);
        }
        else
        {
            out.print(SPACE, JavaTokenTypes.WS);
        }

        AST identifier = type.getNextSibling();
        PrinterFactory.create(identifier).print(identifier, out);
    }
}
