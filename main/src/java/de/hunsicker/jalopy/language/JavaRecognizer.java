/*
 * Copyright (c) 2001-2002, Marco Hunsicker. All rights reserved.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 */
package de.hunsicker.jalopy.language;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import de.hunsicker.antlr.RecognitionException;
import de.hunsicker.antlr.TokenBuffer;
import de.hunsicker.antlr.TokenStreamException;
import de.hunsicker.antlr.TokenStreamHiddenTokenFilter;
import de.hunsicker.antlr.TokenStreamRecognitionException;
import de.hunsicker.antlr.collections.AST;
import de.hunsicker.jalopy.storage.Convention;
import de.hunsicker.jalopy.storage.ConventionDefaults;
import de.hunsicker.jalopy.storage.ConventionKeys;
import de.hunsicker.jalopy.storage.Loggers;

import org.apache.log4j.Level;


/**
 * The Java-specific recognizer.
 *
 * @author <a href="http://jalopy.sf.net/contact.html">Marco Hunsicker</a>
 * @version $Revision$
 */
public final class JavaRecognizer
    extends Recognizer
{
    //~ Static variables/initializers ----------------------------------------------------

    /** Delimeter for encoded tag string. */
    private static final String DELIMETER = "|";

    /** Indicates JDK version 1.3. */
    public static final int JDK_1_3 = JavaLexer.JDK_1_3;

    /** Indicates JDK version 1.4. */
    public static final int JDK_1_4 = JavaLexer.JDK_1_4;

    //~ Instance variables ---------------------------------------------------------------

    /** The code convention. */
    private Convention _settings;

    /** List with the annotations for the current input source. */
    private List _annotations = Collections.EMPTY_LIST; // List of <Annotation>

    /** The position that needs to be tracked. */
    private Position _position;

    /** Resolves wildcard imports. */
    private Transformation _importTrans;

    /** Checks whether debug logging calls are enclosing with a boolean expression . */
    private Transformation _loggingTransformation;

    /** Inserts a serial version UID for serializable classes. */
    private Transformation _serialTrans;

    /** Sorts the AST tree. */
    private Transformation _sortTrans;
    private boolean _trackPosition;

    /** Were the transformations applied to the generated AST? */
    private boolean _transformed;

    //~ Constructors ---------------------------------------------------------------------

    /**
     * Creates a new JavaRecognizer object.
     */
    public JavaRecognizer()
    {
        _settings = Convention.getInstance();

        JavaLexer l = new JavaLexer();
        this.lexer = l;

        JavaParser p = (JavaParser) l.getParser();
        this.parser = p;

        _importTrans =
            new ImportTransformation(p.getQualifiedIdents(), p.getUnqualifiedIdents());
        _sortTrans = new SortTransformation();
        _serialTrans = new SerializableTransformation();
        _loggingTransformation = new LoggerTransformation();
    }

    //~ Methods --------------------------------------------------------------------------

    /**
     * Returns the package name of the parsed source file.
     *
     * @return the package name of the parsed source file. Returns the empty String if
     *         the source file contains no package information.
     *
     * @throws IllegalStateException if the parser is still running or wasn't started
     *         yet.
     */
    public String getPackageName()
    {
        if (!this.finished)
        {
            throw new IllegalStateException("parser not started or still running");
        }

        return ((JavaParser) this.parser).getPackageName();
    }


    /**
     * Returns the root node of the generated parse tree. Note that every call to this
     * method triggers the tree transformations, which could be quite expensive. So make
     * sure to avoid unnecessary calls.
     *
     * <p>
     * As we don't use checked exceptions to indicate runtime failures, one may check
     * successful execution of the transformations prior to perform further processing:
     * <pre class="snippet">
     * if (myJalopyInstance.getState() == Jalopy.State.ERROR)
     * {
     *     // transformation failed, errors were already issued; perform
     *     // any custom error handling code you need
     * }
     * else
     * {
     *     // perform further logic
     * }
     * </pre>
     * </p>
     *
     * @return root node of the generated AST (of type {@link
     *         de.hunsicker.jalopy.language.JavaNode &lt;JavaNode&gt;}).
     *
     * @throws IllegalStateException if the parser is still running or wasn't started
     *         yet.
     *
     * @see de.hunsicker.jalopy.Jalopy#getState
     */
    public AST getParseTree()
    {
        if (!this.finished)
        {
            throw new IllegalStateException("parser not started or still running");
        }

        if (!_transformed)
        {
            boolean trackAnnotations = !_annotations.isEmpty();

            if (_trackPosition || trackAnnotations)
            {
                PositionTracker tracker = new PositionTracker();

                if (trackAnnotations)
                {
                    tracker.annotation = (Annotation) _annotations.get(0);
                }

                tracker.walk(super.getParseTree());
            }

            transform();
            _transformed = true;
        }

        return super.getParseTree();
    }


    /**
     * Returns the tracked position information.
     *
     * @return the tracked position or <code>null</code> if no position should have been
     *         tracked.
     *
     * @since 1.0b9
     */
    public Position getPosition()
    {
        return _position;
    }


    /**
     * Attaches the given annotations to the current input source. All annotations will
     * be associated with the parse tree node that matches their locations.
     *
     * @param annotations list with annotations (of type {@link
     *        de.hunsicker.jalopy.plugin.Annotation &lt;Annotation&gt;}).
     *
     * @see de.hunsicker.jalopy.plugin.Annotation
     * @see #detachAnnotations
     * @since 1.0b9
     */
    public void attachAnnotations(List annotations)
    {
        _annotations = annotations;
    }


    /**
     * Detaches all annotations.
     *
     * @return list with annotations (of type &lt;{@link
     *         de.hunsicker.jalopy.plugin.Annotation}&gt;). Returns an empty list in
     *         case no annotations were attached for the input source.
     *
     * @see #attachAnnotations
     * @since 1.0b9
     */
    public List detachAnnotations()
    {
        try
        {
            return _annotations;
        }
        finally
        {
            if (_annotations != Collections.EMPTY_LIST)
            {
                _annotations = Collections.EMPTY_LIST;
            }
        }
    }


    /**
     * Indicates whether the current tree contains annotations.
     *
     * @return <code>true</code> if the tree contains annotations.
     *
     * @since 1.0b9
     */
    public boolean hasAnnotations()
    {
        return !_annotations.isEmpty();
    }


    /**
     * Determines whether the current tree contains a node that needs its position to be
     * tracked.
     *
     * @return <code>true</code> if the tree contains a node that needs its position to
     *         be tracked.
     *
     * @since 1.0b9
     */
    public boolean hasPosition()
    {
        return _position != null;
    }


    /**
     * Marks a position in the given input source.
     *
     * @param line a valid line number (<code>&gt;= 1</code>).
     * @param column a valid column offset (<code>&gt;= 1</code>).
     *
     * @throws IllegalArgumentException if either <em>line</em> or
     *         <em>column</em><code>&lt; 1</code>
     *
     * @since 1.0b9
     */
    public void markPosition(
        int line,
        int column)
    {
        if ((line < 1) || (column < 1))
        {
            throw new IllegalArgumentException();
        }

        _trackPosition = true;
        _position = new Position(line, column);
    }


    /**
     * {@inheritDoc}
     */
    public void parse(
        Reader in,
        String filename)
    {
        if (this.running)
        {
            throw new IllegalStateException("parser currently running");
        }

        this.finished = false;
        this.running = true;
        _transformed = false;

        // update the parsers/lexer driving settings prior to parsing
        JavaParser parser = (JavaParser) this.parser;
        parser.stripQualification =
            _settings.getBoolean(
                ConventionKeys.STRIP_QUALIFICATION, ConventionDefaults.STRIP_QUALIFICATION);

        JavaLexer lexer = (JavaLexer) this.lexer;
        lexer.setTabSize(
            _settings.getInt(
                ConventionKeys.INDENT_SIZE_TABS, ConventionDefaults.INDENT_SIZE_TABS));
        lexer.sourceVersion =
            _settings.getInt(
                ConventionKeys.SOURCE_VERSION, ConventionDefaults.SOURCE_VERSION);
        lexer.parseJavadocComments =
            _settings.getBoolean(
                ConventionKeys.COMMENT_JAVADOC_PARSE,
                ConventionDefaults.COMMENT_JAVADOC_PARSE);
        lexer.removeJavadocComments =
            _settings.getBoolean(
                ConventionKeys.COMMENT_JAVADOC_REMOVE,
                ConventionDefaults.COMMENT_JAVADOC_REMOVE);
        lexer.removeSLComments =
            _settings.getBoolean(
                ConventionKeys.COMMENT_REMOVE_SINGLE_LINE,
                ConventionDefaults.COMMENT_REMOVE_SINGLE_LINE);
        lexer.removeMLComments =
            _settings.getBoolean(
                ConventionKeys.COMMENT_REMOVE_MULTI_LINE,
                ConventionDefaults.COMMENT_REMOVE_MULTI_LINE);
        lexer.formatMLComments =
            _settings.getBoolean(
                ConventionKeys.COMMENT_FORMAT_MULTI_LINE,
                ConventionDefaults.COMMENT_FORMAT_MULTI_LINE);

        JavadocParser javadocParser = lexer.getJavadocParser();
        javadocParser.setCustomStandardTags(
            decodeTags(
                _settings.get(
                    ConventionKeys.COMMENT_JAVADOC_TAGS_STANDARD,
                    ConventionDefaults.COMMENT_JAVADOC_TAGS_STANDARD)));
        javadocParser.setCustomInlineTags(
            decodeTags(
                _settings.get(
                    ConventionKeys.COMMENT_JAVADOC_TAGS_INLINE,
                    ConventionDefaults.COMMENT_JAVADOC_TAGS_INLINE)));

        this.lexer.setInputBuffer(in);

        TokenStreamHiddenTokenFilter filter =
            new TokenStreamHiddenTokenFilter(this.lexer);

        /**
         * @todo keep WS
         */
        filter.discard(JavaTokenTypes.WS);
        filter.discard(JavaTokenTypes.SEPARATOR_COMMENT);
        filter.hide(JavaTokenTypes.JAVADOC_COMMENT);
        filter.hide(JavaTokenTypes.ML_COMMENT);
        filter.hide(JavaTokenTypes.SPECIAL_COMMENT);
        filter.hide(JavaTokenTypes.SL_COMMENT);

        this.lexer.setFilename(filename);
        this.parser.setFilename(filename);
        this.parser.setTokenBuffer(new TokenBuffer(filter));

        try
        {
            this.parser.parse();
        }

        // the parsers/lexers should never throw any checked exception as we
        // intercept them and print logging messages prior to attempt
        // further parsing, but we want to provide a savety net
        catch (RecognitionException ex)
        {
            throw new ParseException(ex);
        }
        catch (TokenStreamRecognitionException ex)
        {
            throw new ParseException(ex);
        }
        catch (TokenStreamException ex)
        {
            throw new ParseException(ex);
        }
        finally
        {
            this.finished = true;
            this.running = false;
        }
    }


    /**
     * Decodes the given encoded tags string.
     *
     * @param tags encoded tags string.
     *
     * @return collection of the tags.
     *
     * @since 1.0b7
     */
    private Collection decodeTags(String tags)
    {
        List result = new ArrayList();

        for (StringTokenizer i = new StringTokenizer(tags, DELIMETER);
            i.hasMoreElements();)
        {
            result.add(i.nextToken());
        }

        return result;
    }


    /**
     * Applies the registered transformations to the AST.
     */
    private void transform()
    {
        AST tree = this.parser.getParseTree();

        if (tree != null)
        {
            try
            {
                _importTrans.apply(tree);

                if (
                    _settings.getBoolean(
                        ConventionKeys.INSERT_SERIAL_UID,
                        ConventionDefaults.INSERT_SERIAL_UID))
                {
                    _serialTrans.apply(tree);
                }

                if (_settings.getBoolean(ConventionKeys.SORT, ConventionDefaults.SORT))
                {
                    _sortTrans.apply(tree);
                }

                if (
                    _settings.getBoolean(
                        ConventionKeys.INSERT_LOGGING_CONDITIONAL,
                        ConventionDefaults.INSERT_LOGGING_CONDITIONAL))
                {
                    _loggingTransformation.apply(tree);
                }
            }
            catch (TransformationException ex)
            {
                Object[] args = { this.parser.getFilename() };
                Loggers.IO.l7dlog(Level.ERROR, "TRANS_ERROR", args, ex);
            }
        }
    }

    //~ Inner Classes --------------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @todo provide custom walkNode method, only link in children if span contains line
     */
    private final class PositionTracker
        extends TreeWalker
    {
        /** The current annotation that is tracked. */
        Annotation annotation;

        /** The current index of the annotation that is tracked. */
        int index;

        public void visit(AST node)
        {
            // forget about most imaginary nodes, operators and the like to make
            // implementing the tracking logic easier (as only a few printer classes need
            // to implement it)
            switch (node.getType())
            {
                case JavaTokenTypes.CLASS_DEF :
                case JavaTokenTypes.INTERFACE_DEF :
                case JavaTokenTypes.METHOD_DEF :
                case JavaTokenTypes.CTOR_DEF :
                case JavaTokenTypes.STATIC_INIT :
                case JavaTokenTypes.INSTANCE_INIT :
                case JavaTokenTypes.VARIABLE_DEF :
                case JavaTokenTypes.EXTENDS_CLAUSE :
                case JavaTokenTypes.IMPLEMENTS_CLAUSE :
                case JavaTokenTypes.MODIFIERS :
                case JavaTokenTypes.ELIST :
                case JavaTokenTypes.TYPE :
                case JavaTokenTypes.ASSIGN :
                case JavaTokenTypes.DOT :
                case JavaTokenTypes.ARRAY_DECLARATOR :
                case JavaTokenTypes.PARAMETERS :
                case JavaTokenTypes.PARAMETER_DEF :
                case JavaTokenTypes.LABELED_STAT :
                case JavaTokenTypes.TYPECAST :
                case JavaTokenTypes.INDEX_OP :
                case JavaTokenTypes.POST_INC :
                case JavaTokenTypes.POST_DEC :
                case JavaTokenTypes.METHOD_CALL :
                case JavaTokenTypes.EXPR :
                case JavaTokenTypes.ARRAY_INIT :
                case JavaTokenTypes.CASE_GROUP :
                case JavaTokenTypes.FOR_INIT :
                case JavaTokenTypes.FOR_ITERATOR :
                case JavaTokenTypes.FOR_CONDITION :
                case JavaTokenTypes.CTOR_CALL :
                case JavaTokenTypes.SUPER_CTOR_CALL :
                case JavaTokenTypes.LITERAL_new :
                case JavaTokenTypes.LPAREN :
                case JavaTokenTypes.RPAREN :
                case JavaTokenTypes.LBRACK :
                case JavaTokenTypes.RBRACK :
                case JavaTokenTypes.CASESLIST :
                case JavaTokenTypes.PLUS_ASSIGN :
                case JavaTokenTypes.MINUS_ASSIGN :
                case JavaTokenTypes.STAR_ASSIGN :
                case JavaTokenTypes.DIV_ASSIGN :
                case JavaTokenTypes.MOD_ASSIGN :
                case JavaTokenTypes.SR_ASSIGN :
                case JavaTokenTypes.BSR_ASSIGN :
                case JavaTokenTypes.SL_ASSIGN :
                case JavaTokenTypes.BAND_ASSIGN :
                case JavaTokenTypes.BXOR_ASSIGN :
                case JavaTokenTypes.BOR_ASSIGN :
                case JavaTokenTypes.QUESTION :
                case JavaTokenTypes.LOR :
                case JavaTokenTypes.LAND :
                case JavaTokenTypes.BOR :
                case JavaTokenTypes.BXOR :
                case JavaTokenTypes.BAND :
                case JavaTokenTypes.NOT_EQUAL :
                case JavaTokenTypes.EQUAL :
                case JavaTokenTypes.LT :
                case JavaTokenTypes.GT :
                case JavaTokenTypes.LE :
                case JavaTokenTypes.GE :
                case JavaTokenTypes.LITERAL_instanceof :
                case JavaTokenTypes.SL :
                case JavaTokenTypes.SR :
                case JavaTokenTypes.BSR :
                case JavaTokenTypes.PLUS :
                case JavaTokenTypes.MINUS :
                case JavaTokenTypes.DIV :
                case JavaTokenTypes.MOD :
                case JavaTokenTypes.INC :
                case JavaTokenTypes.DEC :
                case JavaTokenTypes.BNOT :
                case JavaTokenTypes.LNOT :
                case JavaTokenTypes.COLON :
                    return;
            }

            JavaNode n = (JavaNode) node;

            if (this.annotation != null)
            {
                if (n.getStartLine() == this.annotation.getLine())
                {
                    n.attachAnnotation(annotation);
                    this.index++;

                    if (_annotations.size() > this.index)
                    {
                        this.annotation = (Annotation) _annotations.get(index);
                    }
                    else
                    {
                        this.annotation = null;

                        if (!_trackPosition)
                        {
                            stop();
                        }
                    }
                }
            }

            if (_trackPosition)
            {
                int line = n.getStartLine();

                if (line == _position.line)
                {
                    System.err.println("node to track " + n + " " + n.hashCode());
                    n.setPosition(_position);
                    _trackPosition = false;

                    if (this.annotation == null)
                    {
                        stop();
                    }
                }
                else if (line < _position.line)
                {
                    /**
                     * @todo store the node next to the position, if no node could be be
                     *       found in the positioned line, use this as position info
                     */
                }
            }
        }
    }
}
