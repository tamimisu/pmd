/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.types.internal.infer

import io.kotest.matchers.shouldBe
import net.sourceforge.pmd.lang.ast.test.shouldBe
import net.sourceforge.pmd.lang.ast.test.shouldMatchN
import net.sourceforge.pmd.lang.java.ast.*
import net.sourceforge.pmd.lang.java.types.*
import net.sourceforge.pmd.lang.java.types.JPrimitiveType.PrimitiveTypeKind.DOUBLE
import net.sourceforge.pmd.lang.java.types.JPrimitiveType.PrimitiveTypeKind.INT
import net.sourceforge.pmd.lang.java.types.testdata.TypeInferenceTestCases


class BranchingExprsTestCases : ProcessorTestSpec({

    fun TypeSystem.stringSupplier() : JTypeMirror = with (TypeDslOf(this)) {
        java.util.function.Supplier::class[gen.t_String]
    }

    parserTest("Test ternary lets context flow") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(ExpressionParsingCtx) {

            "makeThree(true ? () -> \"foo\" : () -> \"bar\")" should parseAs {
                methodCall("makeThree") {

                    argList {
                        ternaryExpr {
                            boolean(true)
                            child<ASTLambdaExpression> {
                                it.typeMirror shouldBe it.typeSystem.stringSupplier()
                                child<ASTLambdaParameterList> { }
                                stringLit("\"foo\"")
                            }
                            child<ASTLambdaExpression> {
                                it.typeMirror shouldBe it.typeSystem.stringSupplier()
                                child<ASTLambdaParameterList> { }
                                stringLit("\"bar\"")
                            }
                        }
                    }
                }
            }
        }
    }

    parserTest("Test ternary infers outer stuff") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(ExpressionParsingCtx) {


            "makeThree(true ? () -> \"foo\" : () -> \"bar\")" should parseAs {

                methodCall("makeThree") {
                    argList {
                        ternaryExpr {
                            it.typeMirror shouldBe it.typeSystem.stringSupplier()

                            boolean(true)
                            child<ASTLambdaExpression> {
                                child<ASTLambdaParameterList> { }
                                stringLit("\"foo\"")
                            }
                            child<ASTLambdaExpression> {
                                child<ASTLambdaParameterList> { }
                                stringLit("\"bar\"")
                            }
                        }
                    }
                }
            }
        }
    }

    parserTest("Test ternary without context lubs params") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(StatementParsingCtx) {

            "var ter = true ? new ArrayList<String>() : new LinkedList<String>();" should parseAs {
                localVarDecl {

                    modifiers { }

                    variableDeclarator("ter") {

                        val lubOfBothLists = with (it.typeDsl) {
                            ts.lub(gen.`t_ArrayList{String}`, gen.`t_LinkedList{String}`)
                        }

                        ternaryExpr {
                            it.typeMirror shouldBe lubOfBothLists
                            boolean(true)
                            with(it.typeDsl) {
                                child<ASTConstructorCall>(ignoreChildren = true) {
                                    it.typeMirror shouldBe gen.`t_ArrayList{String}`
                                }
                                child<ASTConstructorCall>(ignoreChildren = true) {
                                    it.typeMirror shouldBe gen.`t_LinkedList{String}`
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    parserTest("Test switch without context lubs params") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(StatementParsingCtx) {

            """
                var ter = switch(foo) {
                 case 1  -> new ArrayList<String>();
                 case 2  -> new LinkedList<String>();
                 default -> Collections.<String>emptyList();
                };
            """ should parseAs {
                localVarDecl {
                    modifiers { }

                    variableDeclarator("ter") {
                        child<ASTSwitchExpression> {
                            it::getTypeMirror shouldBe it.typeDsl.gen.`t_List{String}`
                            unspecifiedChildren(4)
                        }
                    }
                }
            }

            """
                var ter = switch(foo) {
                 case 1  -> 1;
                 case 2  -> 3;
                 default -> -1d;
                };
            """ should parseAs {
                localVarDecl {
                    modifiers { }

                    variableDeclarator("ter") {
                        child<ASTSwitchExpression> {
                            it::getTypeMirror shouldBe it.typeSystem.DOUBLE
                            unspecifiedChildren(4)
                        }
                    }
                }
            }

            """
                // round 2
                var ter = switch(foo) {
                 case 1  -> 1;
                 case 2  -> 3;
                 default -> -1d;
                };
            """ should parseAs {
                localVarDecl {
                    modifiers { }

                    child<ASTVariableDeclarator> {
                        variableId("ter") {
                            it::isTypeInferred shouldBe true
                            it::getTypeMirror shouldBe it.typeSystem.DOUBLE
                        }
                        unspecifiedChild()
                    }
                }
            }
        }
    }

    parserTest("Test ternary without context promotes primitives") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(StatementParsingCtx) {

            "var ter = true ? 1 : 3;" should parseAs {
                localVarDecl {
                    modifiers { }

                    variableDeclarator("ter") {

                        ternaryExpr {
                            it::getTypeMirror shouldBe it.typeSystem.INT
                            boolean(true)
                            int(1)
                            int(3)
                        }
                    }
                }
            }

            "var ter = true ? 1 : 3.0;" should parseAs {
                localVarDecl {
                    modifiers { }

                    variableDeclarator("ter") {

                        ternaryExpr {
                            it::getTypeMirror shouldBe it.typeSystem.DOUBLE
                            boolean(true)
                            int(1)
                            number(DOUBLE)
                        }
                    }
                }
            }

            "var ter = true ? 1 : 'c';" should parseAs {
                localVarDecl {
                    modifiers { }

                    variableDeclarator("ter") {

                        ternaryExpr {
                            it::getTypeMirror shouldBe it.typeSystem.INT
                            boolean(true)
                            int(1)
                            char('c')
                        }
                    }
                }
            }
        }
    }



    parserTest("Cast context doesn't influence standalone ternary") {

        val acu = parser.parse("""
class Scratch {

    static void putBoolean(byte[] b, int off, boolean val) {
        b[off] = (byte) (val ? 1 : 0);
    }
}

        """.trimIndent())

        val ternary = acu.descendants(ASTConditionalExpression::class.java).firstOrThrow()

        ternary.shouldMatchN {
            ternaryExpr {
                it.typeMirror.shouldBePrimitive(INT)
                variableAccess("val")
                int(1)
                int(0)
            }
        }
    }


    parserTest("Assignment context doesn't influence standalone ternary") {


        inContext(StatementParsingCtx) {

            "double ter = true ? 1 : 3;" should parseAs {
                localVarDecl {
                    modifiers { }
                    primitiveType(DOUBLE)
                    variableDeclarator("ter") {

                        ternaryExpr {
                            it.typeMirror.shouldBePrimitive(INT)

                            boolean(true)
                            int(1)
                            int(3)
                        }
                    }
                }
            }

            "double ter = true ? new Integer(2) : 3;" should parseAs {
                localVarDecl {
                    modifiers { }
                    primitiveType(DOUBLE)
                    variableDeclarator("ter") {

                        ternaryExpr {
                            it.typeMirror.shouldBePrimitive(INT) // unboxed

                            boolean(true)
                            constructorCall {
                                it.typeMirror shouldBe it.typeSystem.INT.box()

                                unspecifiedChildren(2)
                            }
                            int(3)
                        }
                    }
                }
            }

            "double ter = true ? 1 : 3.0;" should parseAs {
                localVarDecl {
                    modifiers { }
                    primitiveType(DOUBLE)
                    variableDeclarator("ter") {

                        ternaryExpr {
                            it.typeMirror.shouldBePrimitive(DOUBLE)

                            boolean(true)
                            int(1)
                            number(DOUBLE)
                        }
                    }
                }
            }

            "double ter = true ? 1 : 'c';" should parseAs {
                localVarDecl {
                    modifiers { }
                    primitiveType(DOUBLE)
                    variableDeclarator("ter") {

                        ternaryExpr {
                            it.typeMirror.shouldBePrimitive(INT)

                            boolean(true)
                            int(1)
                            char('c')
                        }
                    }
                }
            }
        }
    }

    parserTest("Reference ternary with context has type of its target") {

        asIfIn(TypeInferenceTestCases::class.java)

        inContext(StatementParsingCtx) {

            "Object ter = true ? String.valueOf(1) : String.valueOf(2);" should parseAs {
                localVarDecl {
                    modifiers { }
                    classType("Object")
                    variableDeclarator("ter") {

                        ternaryExpr {
                            it.typeMirror shouldBe it.typeSystem.OBJECT // not String

                            boolean(true)
                            methodCall("valueOf") {
                                it.typeMirror shouldBe it.typeSystem.STRING

                                unspecifiedChildren(2)
                            }
                            methodCall("valueOf") {
                                it.typeMirror shouldBe it.typeSystem.STRING

                                unspecifiedChildren(2)
                            }
                        }
                    }
                }
            }

            "String ter = (String) (Object) (true ? String.valueOf(1) : 2);" should parseAs {
                localVarDecl {
                    modifiers { }
                    classType("String")
                    variableDeclarator("ter") {

                        castExpr {
                            unspecifiedChild()
                            castExpr {
                                unspecifiedChild()

                                ternaryExpr {
                                    it.typeMirror shouldBe it.typeSystem.OBJECT

                                    boolean(true)
                                    methodCall("valueOf") {
                                        it.typeMirror shouldBe it.typeSystem.STRING

                                        unspecifiedChildren(2)
                                    }
                                    int(2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

})
