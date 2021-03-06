// This is a generated file. Not intended for manual editing.
package org.mule.lang.dw.parser.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.mule.lang.dw.parser.psi.WeaveTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.mule.lang.dw.parser.psi.*;

public class WeaveAttributeElementImpl extends ASTWrapperPsiElement implements WeaveAttributeElement {

  public WeaveAttributeElementImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull WeaveVisitor visitor) {
    visitor.visitAttributeElement(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof WeaveVisitor) accept((WeaveVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public WeaveAttribute getAttribute() {
    return findChildByClass(WeaveAttribute.class);
  }

  @Override
  @Nullable
  public WeaveConditionalAttribute getConditionalAttribute() {
    return findChildByClass(WeaveConditionalAttribute.class);
  }

  @Override
  @Nullable
  public WeaveEnclosedExpression getEnclosedExpression() {
    return findChildByClass(WeaveEnclosedExpression.class);
  }

}
