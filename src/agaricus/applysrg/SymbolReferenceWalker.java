package agaricus.applysrg;

import com.intellij.psi.*;

import java.util.HashMap;

/**
 * Recursively descends and processes symbol references
 */
public class SymbolReferenceWalker {
    private SymbolRangeEmitter emitter;
    private String className;
    private String methodName = "(outside-method)";
    private String methodSignature = "";

    /**
     * Variables in the code block, mapped to the order they were declared.
     * This includes PsiLocalVariable from PsiDeclarationStatement, and also
     * PsiParameter from PsiForeachStatement/PsiCatchSection. Both are PsiVariable.
     */
    private HashMap<PsiVariable, Integer> localVariableIndices = new HashMap<PsiVariable, Integer>();
    private int nextLocalVariableIndex = 0;

    /**
     * Parameters to method, mapped to order in method declaration.
     * Set by caller
     * @see #addMethodParameterIndices
     */
    private HashMap<PsiParameter, Integer> methodParameterIndices = new HashMap<PsiParameter, Integer>();

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className) {
        this.emitter = emitter;
        this.className = className;
    }

    public SymbolReferenceWalker(SymbolRangeEmitter emitter, String className, String methodName, String methodSignature) {
        this(emitter, className);
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    /**
     * Recursively walk starting from given element
     * @param startElement
     */
    public void walk(PsiElement startElement) {
        walk(startElement, 0);
    }

    /**
     * Add map used for labeling method parameters by index
     * @param methodParameterIndices
     */
    public void addMethodParameterIndices(HashMap<PsiParameter, Integer> methodParameterIndices) {
        this.methodParameterIndices.putAll(methodParameterIndices);
    }


    private void walk(PsiElement psiElement, int depth) {
        //System.out.println("walking "+className+" "+psiMethod.getName()+" -- "+psiElement);

        if (psiElement == null) {
            return;
        }

        // New local variable declaration
        if (psiElement instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement psiDeclarationStatement = (PsiDeclarationStatement)psiElement;

            for (PsiElement declaredElement : psiDeclarationStatement.getDeclaredElements()) {
                if (declaredElement instanceof PsiClass) {
                    System.out.println("TODO: inner class "+declaredElement); // TODO: process this?
                } else if (declaredElement instanceof PsiLocalVariable) {
                    PsiLocalVariable psiLocalVariable = (PsiLocalVariable)declaredElement;

                    emitter.emitTypeRange(psiLocalVariable.getTypeElement());
                    emitter.emitLocalVariableRange(className, methodName, methodSignature, psiLocalVariable, nextLocalVariableIndex);

                    // Record order of variable declarations for references in body
                    localVariableIndices.put(psiLocalVariable, nextLocalVariableIndex);
                    nextLocalVariableIndex++;
                } else {
                    System.out.println("WARNING: Unknown declaration "+psiDeclarationStatement);
                }
            }
        }

        // New local variable declaration within try..catch
        if (psiElement instanceof PsiCatchSection) {
            PsiCatchSection psiCatchSection = (PsiCatchSection)psiElement;
            PsiParameter psiParameter = psiCatchSection.getParameter();
            emitter.emitLocalVariableRange(className, methodName, methodSignature, psiParameter, nextLocalVariableIndex);

            localVariableIndices.put(psiParameter, nextLocalVariableIndex);
            nextLocalVariableIndex++;
        }

        // .. and foreach
        if (psiElement instanceof PsiForeachStatement) {
            PsiForeachStatement psiForeachStatement = (PsiForeachStatement)psiElement;
            PsiParameter psiParameter = psiForeachStatement.getIterationParameter();

            emitter.emitLocalVariableRange(className, methodName, methodSignature, psiParameter, nextLocalVariableIndex);

            localVariableIndices.put(psiParameter, nextLocalVariableIndex);
            nextLocalVariableIndex++;
        }

        // Variable reference
        if (psiElement instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = (PsiJavaCodeReferenceElement)psiElement;

            // What this reference expression actually refers to
            PsiElement referentElement = psiJavaCodeReferenceElement.resolve();

            // Identifier token naming this reference without qualification
            PsiElement nameElement = psiJavaCodeReferenceElement.getReferenceNameElement();

            if (referentElement instanceof PsiPackage) {
                // Not logging package since includes net, net.minecraft, net.minecraft.server.. all components
                // TODO: log reference for rename
                //System.out.println("PKGREF"+referentElement+" name="+nameElement);
            } else if (referentElement instanceof PsiClass) {
                emitter.emitReferencedClass(nameElement, (PsiClass)referentElement);
            } else if (referentElement instanceof PsiField) {
                emitter.emitReferencedField(nameElement, (PsiField)referentElement);
            } else if (referentElement instanceof PsiMethod) {
                emitter.emitReferencedMethod(nameElement, (PsiMethod)referentElement);
            } else if (referentElement instanceof PsiLocalVariable) {
                PsiLocalVariable psiLocalVariable = (PsiLocalVariable)referentElement;

                // Index of local variable as declared in method
                int index;
                if (!localVariableIndices.containsKey(psiLocalVariable))  {
                    index = -1;
                    System.out.println("couldn't find local variable index for "+psiLocalVariable+" in "+localVariableIndices);
                } else {
                    index = localVariableIndices.get(psiLocalVariable);
                }

                emitter.emitReferencedLocalVariable(nameElement, className, methodName, methodSignature, psiLocalVariable, index);
            } else if (referentElement instanceof PsiParameter) {
                PsiParameter psiParameter = (PsiParameter)referentElement;

                PsiElement declarationScope = psiParameter.getDeclarationScope();

                if (declarationScope instanceof PsiMethod) {
                    // Method parameter

                    int index;
                    if (!methodParameterIndices.containsKey(psiParameter)) {
                        index = -1;
                        // TODO: properly handle parameters in inner classes.. currently we always look at the outer method,
                        // but there could be parameters in a method in an inner class. This currently causes four errors in CB,
                        // CraftTask and CraftScheduler, since it makes heavy use of anonymous inner classes.
                        System.out.println("WARNING: couldn't find method parameter index for "+psiParameter+" in "+methodParameterIndices);
                    } else {
                        index = methodParameterIndices.get(psiParameter);
                    }

                    emitter.emitReferencedMethodParameter(nameElement, className, methodName, methodSignature, psiParameter, index);
                } else if (declarationScope instanceof PsiForeachStatement || declarationScope instanceof PsiCatchSection) {
                    // New variable declared with for(type var:...) and try{}catch(type var){}
                    // For some reason, PSI calls these "parameters", but they're more like local variable declarations
                    // Treat them as such

                    int index;
                    if (!localVariableIndices.containsKey(psiParameter)) {
                        index = -1;
                        System.out.println("WARNING: couldn't find non-method parameter index for "+psiParameter+" in "+localVariableIndices);
                    } else {
                        index = localVariableIndices.get(psiParameter);
                    }
                    emitter.emitTypeRange(psiParameter.getTypeElement());
                    emitter.emitReferencedLocalVariable(nameElement, className, methodName, methodSignature, psiParameter, index);
                } else {
                    System.out.println("WARNING: parameter "+psiParameter+" in unknown declaration scope "+declarationScope);
                }
            } else {
                // If you get this in a bunch of places on in CB on Entity getBukkitEntity() etc. (null referent), its probably
                // IntelliJ getting confused by the Entity class in the server jar added as a library - but overridden
                // in the project. To fix this, replace your jar with the slimmed version at https://github.com/agaricusb/MinecraftRemapping/blob/master/slim-jar.py
                System.out.println("WARNING: ignoring unknown referent "+referentElement+" in "+className+" "+methodName+","+methodSignature);
            }

            /*
            System.out.println("   ref "+psiReferenceExpression+
                    " nameElement="+nameElement+
                    " name="+psiReferenceExpression.getReferenceName()+
                    " resolve="+psiReferenceExpression.resolve()+
                    " text="+psiReferenceExpression.getText()+
                    " qualifiedName="+psiReferenceExpression.getQualifiedName()+
                    " qualifierExpr="+psiReferenceExpression.getQualifierExpression()
                );
                */
        }

        PsiElement[] children = psiElement.getChildren();
        if (children != null) {
            for (PsiElement child: children) {
                walk(child, depth + 1);
            }
        }
    }
}
