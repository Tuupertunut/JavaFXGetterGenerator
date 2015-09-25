/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lynden.netbeans.javafx;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class JavaFxBeanHelper implements CodeGenerator {

    protected JTextComponent textComponent;
    protected List<VariableElement> fields;

    
    
    
    
    
    public JavaFxBeanHelper textComponent(final JTextComponent value) {
        this.textComponent = value;
        return this;
    }

    public JavaFxBeanHelper fields(final List<VariableElement> value) {
        this.fields = value;
        return this;
    }

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private JavaFxBeanHelper(Lookup context) { // Good practice is not to save Lookup outside ctor
        textComponent = context.lookup(JTextComponent.class);
           CompilationController controller =
                    context.lookup(CompilationController.class);
        try {
            fields = getFields(context, controller);
        } catch (CodeGeneratorException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        public List<? extends CodeGenerator> create(Lookup context) {
            return Collections.singletonList(new JavaFxBeanHelper(context));
        }
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    @Override
    public String getDisplayName() {
        return "JavaFx Props Getter & Setter";
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    @Override
    public void invoke() {
        Document doc = textComponent.getDocument();
        JavaSource javaSource = JavaSource.forDocument(doc);

        CancellableTask<WorkingCopy> task
                = new CodeGeneratorCancellableTask(textComponent) {

                    @Override
                    public void generateCode(WorkingCopy workingCopy, TreePath path,
                            int position) {
                        JavaFxBeanHelper.this
                        .generateCode(workingCopy, path, position,
                                JavaFxBeanHelper.this.fields);
                    }

                };

        try {
            ModificationResult result = javaSource.runModificationTask(task);
            result.commit();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    protected void generateCode(WorkingCopy wc,
            TreePath path,
            int position,
            List<VariableElement> fields) {

        TypeElement typeClassElement = (TypeElement) wc.getTrees().getElement(path);
        if (typeClassElement != null) {
            int index = position;

            TreeMaker make = wc.getTreeMaker();
            ClassTree classTree = (ClassTree) path.getLeaf();
            List<Tree> members = new ArrayList<>(classTree.getMembers());
            String className = typeClassElement.toString();

            PropertyMethodBuilder propertyMethodBuilder
                    = new PropertyMethodBuilder(make, members, fields, className);

            index = propertyMethodBuilder.removeExistingFluentSetters(index);

            propertyMethodBuilder.addFluentSetters(index);

            ClassTree newClassTree = make.Class(classTree.getModifiers(),
                    classTree.getSimpleName(),
                    classTree.getTypeParameters(),
                    classTree.getExtendsClause(),
                    (List<ExpressionTree>) classTree.getImplementsClause(),
                    members);

            wc.rewrite(classTree, newClassTree);
        }
    }
    
    
       private List<VariableElement> getFields(Lookup context,
            CompilationController controller)
            throws CodeGeneratorException {
        try {
            TreePath treePath = context.lookup(TreePath.class);

            TreePath path = TreeHelper
                    .getParentElementOfKind(Tree.Kind.CLASS, treePath);

            TypeElement typeElement = (TypeElement)
                    controller.getTrees().getElement(path);

            if (!typeElement.getKind().isClass()) {
                throw new CodeGeneratorException("typeElement " +
                        typeElement.getKind().name() +
                        " is not a class, cannot generate code.");
            }

            Elements elements = controller.getElements();

            return ElementFilter.fieldsIn(elements.getAllMembers(typeElement));

        } catch (NullPointerException ex) {
            throw new CodeGeneratorException(ex);
        }
    }

           private static class CodeGeneratorException extends Exception {
        private static final long serialVersionUID = 1L;

        public CodeGeneratorException(String message) {
            super(message);
        }

        public CodeGeneratorException(Throwable cause) {
            super(cause);
        }
    }
       
}
