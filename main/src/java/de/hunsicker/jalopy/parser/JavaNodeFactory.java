/*
 * Copyright (c) 2001-2002, Marco Hunsicker. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the Jalopy project nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * $Id$
 */
package de.hunsicker.jalopy.parser;

import de.hunsicker.antlr.ASTFactory;
import de.hunsicker.antlr.ASTPair;
import de.hunsicker.antlr.Token;
import de.hunsicker.antlr.collections.AST;
import de.hunsicker.jalopy.storage.Loggers;


/**
 * Central facility to create the nodes for the JavaNode.
 *
 * @author <a href="http://jalopy.sf.net/contact.html">Marco Hunsicker</a>
 * @version $Revision$
 */
public class JavaNodeFactory
    extends ASTFactory
{
    //~ Static variables/initializers иииииииииииииииииииииииииииииииииииииииии

    private static final String EMPTY_STRING = "";

    //~ Constructors ииииииииииииииииииииииииииииииииииииииииииииииииииииииииии

    /**
     * Creates a new JavaNodeFactory object.
     */
    public JavaNodeFactory()
    {
        this.theASTNodeType = "JavaNode";
        this.theASTNodeTypeClass = JavaNode.class;
    }

    //~ Methods иииииииииииииииииииииииииииииииииииииииииииииииииииииииииииииии

    public void makeASTRoot(ASTPair currentAST, AST root) {
        if (root != null)
        {
            // Add the current root as a child of new root
            root.addChild(currentAST.root);

            // The new current child is the last sibling of the old root
            currentAST.child = currentAST.root;
            currentAST.advanceChildToEnd();

            // update the parent link for all siblings
            for (JavaNode sibling = (JavaNode)currentAST.root;
                 sibling != null; sibling = (JavaNode)sibling.getNextSibling())
            {
                sibling.parent = (JavaNode)root;
            }

            // Set the new root
            currentAST.root = root;
        }
    }

    /**
     * Add the given node as a child to the given root.
     *
     * @param currentAST root pair.
     * @param child new child to add.
     */
    public void addASTChild(ASTPair currentAST,
                            AST     child)
    {
        if (child != null)
        {
            JavaNode newChild = (JavaNode)child;

            if (currentAST.root == null)
            {
                // make new child the current root
                currentAST.root = newChild;
            }
            else
            {
                JavaNode root = (JavaNode)currentAST.root;

                if ((root.getType() == JavaTokenTypes.EXPR) &&
                    (newChild.getType() == JavaTokenTypes.EXPR))
                {
                    ;
                }
                else if (newChild.isPositionKnown())
                {
                    root.endLine = newChild.endLine;
                    root.endColumn = newChild.endColumn;
                }

                // add new child to current root
                if (currentAST.child == null)
                {
                    currentAST.root.setFirstChild(newChild);
                    newChild.parent = root;
                    newChild.prevSibling = root;
                }
                else
                {
                    currentAST.child.setNextSibling(newChild);
                    newChild.parent = root;
                    newChild.prevSibling = (JavaNode)currentAST.child;
                }

                // update the parent link for all siblings
                for (JavaNode sibling = (JavaNode)newChild.getNextSibling();
                     sibling != null;
                     sibling = (JavaNode)sibling.getNextSibling())
                {
                    sibling.parent = root;
                }
           }

            currentAST.child = child;
            currentAST.advanceChildToEnd();
        }
    }


    /**
     * Creates a new empty JavaNode node.
     *
     * @return newly created Node.
     */
    public AST create()
    {
        return new JavaNode();
    }


    /**
     * Creates a new JavaNode node.
     *
     * @param type information to setup the node with.
     *
     * @return newly created Node.
     */
    public AST create(int type)
    {
        JavaNode node = (JavaNode)create();
        node.initialize(type, EMPTY_STRING);

        return node;
    }


    /**
     * Creates a new JavaNode node.
     *
     * @param node node to setup the new node with.
     *
     * @return newly created Node.
     */
    public AST create(AST node)
    {
        if (node == null)
        {
            return null;
        }

        JavaNode result = (JavaNode)create();
        result.initialize(node);

        return result;
    }


    /**
     * Creates a new JavaNode node.
     *
     * @param token token to setup the new node with.
     *
     * @return newly created Node.
     */
    public AST create(Token token)
    {
        if (token == null)
        {
            return null;
        }

        JavaNode result = (JavaNode)create();
        result.initialize(token);

        return result;
    }


    /**
     * Creates a new JavaNode node.
     *
     * @param type type information to setup the node with.
     * @param text text to setup the node with.
     *
     * @return newly created Node.
     */
    public AST create(int    type,
                      String text)
    {
        JavaNode result = (JavaNode)create();
        result.initialize(type, text);

        return result;
    }


    /**
     * Duplicate the given tree (including all siblings of root).
     *
     * @param t DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public AST dupList(AST t)
    {
        JavaNode result = (JavaNode)dupTree(t); // if t == null, then result==null

        if (result != null)
        {
            JavaNode node = (JavaNode)t;
            result.parent = node.parent;
            result.prevSibling = node.parent;
        }

        JavaNode nt = result;

        while (t != null) // for each sibling of the root
        {
            t = t.getNextSibling();

            JavaNode next = (JavaNode)dupTree(t);
            nt.setNextSibling(next); // dup each subtree, building new tree

            if (next != null)
            {
                next.prevSibling = nt;
                next.parent = result.parent;
            }

            nt = (JavaNode)nt.getNextSibling();
        }

        return result;
    }


    /**
     * Duplicate a tree, assuming this is a root node of a tree-- duplicate
     * that node and what's below; ignore siblings of root node.
     *
     * @param t DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public AST dupTree(AST t)
    {
        JavaNode result = (JavaNode)dup(t); // make copy of root



        // copy all children of root.
        if (t != null)
        {
            JavaNode child = (JavaNode)t.getFirstChild();

            if (child != null)
            {
                child.parent = result;
                child.prevSibling = result;
                result.setFirstChild(dupList(child));
            }
        }

        return result;
    }


    /**
     * DOCUMENT ME!
     *
     * @param message DOCUMENT ME!
     */
    public void error(String message)
    {
        Loggers.PARSER.error(message);
    }


    /**
     * Makes a tree from a list of nodes. The first element in the array is
     * the root. If the root is <code>null</code>, then the tree is actually
     * a simple list not a tree. Handles <code>null</code> children nodes
     * correctly. For example, <code>build(a, b,  null, c)</code> yields
     * <code>tree (a b c)</code>. <code>build(null,a,b)</code> yields tree
     * <code>(nil a b)</code>.
     *
     * <p>
     * Sets also the line/column info of the root node.
     * </p>
     *
     * @param nodes the nodes to create the tree with.
     *
     * @return the generated tree.
     */
    public AST make(AST[] nodes)
    {
        if ((nodes == null) || (nodes.length == 0))
        {
            return null;
        }

        JavaNode root = (JavaNode)nodes[0];

        if (root != null)
        {
            // don't leave any old pointers set
            root.setFirstChild(null);
        }

        // was the position info set
        boolean set = root.isPositionKnown();
        JavaNode tail = null;

        // link in children
        for (int i = 1; i < nodes.length; i++)
        {
            if (nodes[i] == null)
            {
                continue;
            }

            JavaNode first = (JavaNode)nodes[i];

            if (root == null)
            {
                // set the root and set it up for a flat list
                root = tail = (JavaNode)nodes[i];
            }
            else if (tail == null)
            {
                root.setFirstChild(nodes[i]);
                tail = first;
                tail.parent = root;
                tail.prevSibling = root;
            }
            else
            {
                tail.setNextSibling(nodes[i]);

                JavaNode tmp = tail;
                tail = first;
                tail.parent = root;
                tail.prevSibling = tmp;
            }

            // set the root position from the first child were the position
            // is known
            if ((!set) && first.isPositionKnown())
            {
                // make sure the first node holds the first token
                first = getFirstNode(first);
                root.startLine = first.startLine;
                root.startColumn = first.startColumn;
                root.endLine = first.endLine;
                root.endColumn = first.endColumn;
                set = true;
            }

            // chase tail to last sibling
            while (tail.getNextSibling() != null)
            {
                JavaNode prevSibling = tail;
                tail = (JavaNode)tail.getNextSibling();
                tail.parent = root;
                tail.prevSibling = prevSibling;
            }
        }

        if (root.isPositionKnown())
        {
            if (tail.isPositionKnown())
            {
                root.endLine = tail.endLine;
                root.endColumn = tail.endColumn;
            }
            else
            {
                tail.startLine = root.startLine;
                tail.startColumn = root.startColumn + 1;
                tail.endLine = root.endLine;
                tail.endColumn = root.endColumn - 1;
            }
        }

        return root;
    }


    /**
     * Returns the node which holds the actual first token (as found in the
     * input source).  For dotted AST portions that means we have to link
     * into the children to find the correct one (which is the last in the
     * AST portion).
     *
     * @param node node to search for the actual first node.
     *
     * @return the actual first node (normally <em>node</em> is returned).
     */
    private JavaNode getFirstNode(AST node)
    {
        switch (node.getType())
        {
            case JavaTokenTypes.DOT :
            case JavaTokenTypes.METHOD_CALL :
                return (JavaNode)getFirstNode(node.getFirstChild());
        }

        return (JavaNode)node;
    }
}
