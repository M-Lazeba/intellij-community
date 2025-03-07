// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.PerFileConfigurableBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemplateDataLanguageConfigurable extends PerFileConfigurableBase<Language> {
  public TemplateDataLanguageConfigurable(@NotNull Project project) {
    super(project, TemplateDataLanguageMappings.getInstance(project));
  }

  @Override
  public @Nls String getDisplayName() {
    return LangBundle.message("template.data.language.configurable");
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return "reference.settingsdialog.project.template.languages";
  }

  @Override
  protected <S> Object getParameter(@NotNull Key<S> key) {
    if (key == DESCRIPTION) return LangBundle.message("dialog.template.data.language.caption", ApplicationNamesInfo.getInstance().getFullProductName());
    if (key == MAPPING_TITLE) return LangBundle.message("template.data.language.configurable.tree.table.title");
    if (key == OVERRIDE_QUESTION) return LangBundle.message("template.data.language.override.warning.text");
    if (key == OVERRIDE_TITLE) return LangBundle.message("template.data.language.override.warning.title");
    return null;
  }

  @Override
  protected void renderValue(@Nullable Object target, @NotNull Language language, @NotNull ColoredTextContainer renderer) {
    renderer.append(language.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(ObjectUtils.notNull(language.getAssociatedFileType(), FileTypes.UNKNOWN).getIcon());
  }

  @Override
  protected void renderDefaultValue(Object target, @NotNull ColoredTextContainer renderer) {
    Language language = TemplateDataLanguagePatterns.getInstance().getTemplateDataLanguageByFileName((VirtualFile)target);
    if (language == null) return;
    renderer.append(language.getDisplayName(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    renderer.setIcon(ObjectUtils.notNull(language.getAssociatedFileType(), FileTypes.UNKNOWN).getIcon());
  }
}
