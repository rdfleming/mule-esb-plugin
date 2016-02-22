package org.mule.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.mulesoft.mule.debugger.commons.Breakpoint;
import com.mulesoft.mule.debugger.commons.MessageProcessorPath;
import com.mulesoft.mule.debugger.commons.MessageProcessorPathNode;
import com.mulesoft.mule.debugger.commons.MessageProcessorPathType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mule.config.MuleConfigConstants;
import org.mule.config.model.Flow;
import org.mule.config.model.Mule;
import org.mule.config.model.SubFlow;

import javax.xml.namespace.QName;
import java.util.*;

public class MuleSupport {

    public static final String FLOW_LOCAL_NAME = "flow";
    public static final String MULE_LOCAL_NAME = "mule";

    public static boolean isMuleFile(PsiFile psiFile) {
        if (psiFile.getFileType() != StdFileTypes.XML) return false;
        if (!(psiFile instanceof XmlFile)) return false;
        final XmlFile psiFile1 = (XmlFile) psiFile;
        final XmlTag rootTag = psiFile1.getRootTag();
        return isMuleConfig(rootTag);
    }

    private static boolean isMuleConfig(XmlTag rootTag) {
        return rootTag.getLocalName().equalsIgnoreCase(MULE_LOCAL_NAME);
    }

    public static QName getQName(XmlTag xmlTag) {
        return new QName(xmlTag.getNamespace(), xmlTag.getLocalName());
    }

    @Nullable
    public static XmlTag findFlow(Project project, String flowName) {
        final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
        return findFlowInScope(project, flowName, searchScope);
    }


    @Nullable
    public static XmlTag findFlow(Module module, String flowName) {
        final GlobalSearchScope searchScope = GlobalSearchScope.moduleScope(module);
        return findFlowInScope(module.getProject(), flowName, searchScope);
    }

    public static List<DomElement> getFlows(Module module) {
        final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependenciesScope(module);
        return getFlowsInScope(module.getProject(), searchScope);
    }

    public static List<DomElement> getFlows(Project project) {
        final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
        return getFlowsInScope(project, searchScope);
    }

    @NotNull
    private static List<DomElement> getFlowsInScope(Project project, GlobalSearchScope searchScope) {
        final List<DomElement> result = new ArrayList<>();
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(StdFileTypes.XML, searchScope);
        final DomManager manager = DomManager.getDomManager(project);
        for (VirtualFile file : files) {
            final PsiFile xmlFile = PsiManager.getInstance(project).findFile(file);
            if (isMuleFile(xmlFile)) {
                final DomFileElement<Mule> fileElement = manager.getFileElement((XmlFile) xmlFile, Mule.class);
                if (fileElement != null) {
                    final Mule rootElement = fileElement.getRootElement();
                    result.addAll(rootElement.getFlows());
                    result.addAll(rootElement.getSubFlows());
                }
            }
        }
        return result;
    }

    @Nullable
    private static XmlTag findFlowInScope(Project project, String flowName, GlobalSearchScope searchScope) {
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(StdFileTypes.XML, searchScope);
        final DomManager manager = DomManager.getDomManager(project);
        for (VirtualFile file : files) {
            final PsiFile xmlFile = PsiManager.getInstance(project).findFile(file);
            if (isMuleFile(xmlFile)) {
                final DomFileElement<Mule> fileElement = manager.getFileElement((XmlFile) xmlFile, Mule.class);
                if (fileElement != null) {
                    final Mule rootElement = fileElement.getRootElement();
                    final List<Flow> flows = rootElement.getFlows();
                    for (Flow flow : flows) {
                        if (flowName.equals(flow.getName().getValue())) {
                            return flow.getXmlTag();
                        }
                    }
                    final List<SubFlow> subFlows = rootElement.getSubFlows();
                    for (SubFlow subFlow : subFlows) {
                        if (flowName.equals(subFlow.getName().getValue())) {
                            return subFlow.getXmlTag();
                        }
                    }
                }
            }
        }
        return null;
    }


    @NotNull
    public static List<XmlTag> findFlowRef(Project project, String flowName) {
        List<XmlTag> flowRefs = new ArrayList<>();
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(StdFileTypes.XML, GlobalSearchScope.projectScope(project));
        final DomManager manager = DomManager.getDomManager(project);
        for (VirtualFile file : files) {
            final PsiFile xmlFile = PsiManager.getInstance(project).findFile(file);
            if (isMuleFile(xmlFile)) {
                final DomFileElement<Mule> fileElement = manager.getFileElement((XmlFile) xmlFile, Mule.class);
                if (fileElement != null) {
                    final Mule rootElement = fileElement.getRootElement();
                    final XmlTag rootTag = rootElement.getXmlTag();
                    addSubFlowsNamed(flowRefs, rootTag, flowName);
                }
            }
        }
        return flowRefs;
    }

    private static void addSubFlowsNamed(List<XmlTag> flowRefs, XmlTag rootTag, String flowName) {
        final XmlTag[] subTags = rootTag.getSubTags();
        for (XmlTag subTag : subTags) {
            if (subTag.getLocalName().equals(MuleConfigConstants.FLOW_REF_TAG_NAME) && flowName.equals(subTag.getAttributeValue(MuleConfigConstants.NAME_ATTRIBUTE))) {
                flowRefs.add(subTag);
            } else {
                addSubFlowsNamed(flowRefs, subTag, flowName);
            }
        }
    }

    public static XmlTag getTagAt(Project project, String path) {
        final MessageProcessorPath messageProcessorPath = MessageProcessorPath.fromPath(path);
        final MessageProcessorPathType type = messageProcessorPath.getType();
        final String flowName = messageProcessorPath.getFlowName();
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(StdFileTypes.XML, GlobalSearchScope.projectScope(project));
        final DomManager manager = DomManager.getDomManager(project);
        for (VirtualFile file : files) {
            final PsiFile xmlFile = PsiManager.getInstance(project).findFile(file);
            if (isMuleFile(xmlFile)) {
                final Mule rootElement = manager.getFileElement((XmlFile) xmlFile, Mule.class).getRootElement();
                switch (type) {
                    case processors:
                        final List<Flow> flows = rootElement.getFlows();
                        for (Flow flow : flows) {
                            if (flow.getName().getXmlAttributeValue().getValue().equals(flowName)) {
                                XmlTag xmlTag = flow.getXmlTag();
                                return findChildMessageProcessorByPath(messageProcessorPath, xmlTag);
                            }
                        }
                        break;
                    case subprocessors:
                        final List<SubFlow> subFlows = rootElement.getSubFlows();
                        for (SubFlow subFlow : subFlows) {
                            final XmlAttributeValue xmlAttributeValue = subFlow.getName().getXmlAttributeValue();
                            if (xmlAttributeValue != null && xmlAttributeValue.getValue().equals(flowName)) {
                                XmlTag xmlTag = subFlow.getXmlTag();
                                return findChildMessageProcessorByPath(messageProcessorPath, xmlTag);
                            }
                        }
                        break;
                    default:
                        final XmlTag[] subTags = ((XmlFile) xmlFile).getRootTag().getSubTags();
                        for (XmlTag subTag : subTags) {
                            final XmlAttribute name = subTag.getAttribute("name");
                            if (name != null && name.getValue() != null && name.getValue().equals(flowName)) {
                                return findChildMessageProcessorByPath(messageProcessorPath, subTag);
                            }
                        }
                        break;
                }
            }
        }
        return null;
    }

    private static XmlTag findChildMessageProcessorByPath(MessageProcessorPath messageProcessorPath, XmlTag xmlTag) {
        final List<MessageProcessorPathNode> nodes = messageProcessorPath.getNodes();
        for (MessageProcessorPathNode node : nodes) {
            final String elementName = node.getElementName();
            final int i = Integer.parseInt(elementName);
            final XmlTag[] subTags = xmlTag.getSubTags();
            int index = -1;
            for (XmlTag subTag : subTags) {
                final MessageProcessorType messageProcessorType = getMessageProcessorType(subTag);
                if (messageProcessorType == MessageProcessorType.MESSAGE_PROCESSOR || messageProcessorType == MessageProcessorType.CONDITIONAL_ROUTE) {
                    xmlTag = subTag;
                    index = index + 1;
                }
                if (index == i) {
                    break;
                }
            }
        }
        return xmlTag;
    }


    @Nullable
    public static XSourcePosition createPositionByElement(PsiElement element) {
        if (element == null) return null;

        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null) return null;

        final VirtualFile file = psiFile.getVirtualFile();
        if (file == null) return null;

        final SmartPsiElementPointer<PsiElement> pointer =
                SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

        return new XSourcePosition() {
            private volatile XSourcePosition myDelegate;

            private XSourcePosition getDelegate() {
                if (myDelegate == null) {
                    myDelegate = ApplicationManager.getApplication().runReadAction(new Computable<XSourcePosition>() {
                        @Override
                        public XSourcePosition compute() {
                            PsiElement elem = pointer.getElement();
                            return XSourcePositionImpl.createByOffset(pointer.getVirtualFile(), elem != null ? elem.getTextOffset() : -1);
                        }
                    });
                }
                return myDelegate;
            }

            @Override
            public int getLine() {
                return getDelegate().getLine();
            }

            @Override
            public int getOffset() {
                return getDelegate().getOffset();
            }

            @NotNull
            @Override
            public VirtualFile getFile() {
                return file;
            }

            @NotNull
            @Override
            public Navigatable createNavigatable(@NotNull Project project) {
                // no need to create delegate here, it may be expensive
                if (myDelegate != null) {
                    return myDelegate.createNavigatable(project);
                }
                PsiElement elem = pointer.getElement();
                if (elem instanceof Navigatable) {
                    return ((Navigatable) elem);
                }
                return NonNavigatable.INSTANCE;
            }
        };
    }

    @NotNull
    public static Breakpoint toMuleBreakpoint(Project project, XLineBreakpoint<XBreakpointProperties> lineBreakpoint) {
        final XSourcePosition sourcePosition = lineBreakpoint.getSourcePosition();
        final XExpression conditionExpression = lineBreakpoint.getConditionExpression();
        final String conditionScript = conditionExpression != null ? asMelScript(conditionExpression.getExpression()) : null;
        return new Breakpoint(getMulePath(project, sourcePosition), conditionScript, project.getName());
    }

    @NotNull
    public static String getMulePath(Project project, XSourcePosition sourcePosition) {
        final XmlTag tag = getXmlTagAt(project, sourcePosition);
        return getMulePath(tag);
    }

    @Nullable
    public static XmlTag getXmlTagAt(Project project, XSourcePosition sourcePosition) {
        final VirtualFile file = sourcePosition.getFile();
        final XmlFile xmlFile = (XmlFile) PsiManager.getInstance(project).findFile(file);
        final XmlTag rootTag = xmlFile.getRootTag();
        return findXmlTag(sourcePosition, rootTag);
    }

    private static XmlTag findXmlTag(XSourcePosition sourcePosition, XmlTag rootTag) {
        final XmlTag[] subTags = rootTag.getSubTags();
        for (int i = 0; i < subTags.length; i++) {
            XmlTag subTag = subTags[i];
            final int subTagLineNumber = getLineNumber(sourcePosition.getFile(), subTag);
            if (subTagLineNumber == sourcePosition.getLine()) {
                return subTag;
            } else if (subTagLineNumber > sourcePosition.getLine() && i > 0 && subTags[i - 1].getSubTags().length > 0) {
                return findXmlTag(sourcePosition, subTags[i - 1]);
            }
        }
        if (subTags.length > 0) {
            final XmlTag lastElement = subTags[subTags.length - 1];
            return findXmlTag(sourcePosition, lastElement);
        } else {
            return null;
        }
    }

    public static int getLineNumber(VirtualFile file, XmlTag tag) {
        final int offset = tag.getTextOffset();
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        return offset < document.getTextLength() ? document.getLineNumber(offset) : -1;
    }

    public static String getMulePath(XmlTag tag) {
        final LinkedList<XmlTag> elements = new LinkedList<>();
        while (!isMuleConfig(tag)) {
            elements.push(tag);
            tag = tag.getParentTag();
        }
        String path = "";
        for (int i = 0; i < elements.size(); i++) {
            final XmlTag element = elements.get(i);
            switch (i) {
                case 0: {
                    final XmlAttribute name = element.getAttribute(MuleConfigConstants.NAME_ATTRIBUTE);
                    if (name != null) {
                        path = "/" + name.getValue() + getGlobalElementCategory(element);
                    }
                    break;
                }
                default: {
                    final XmlTag parentTag = element.getParentTag();
                    int index = 0;
                    for (XmlTag xmlTag : parentTag.getSubTags()) {
                        if (xmlTag == element) {
                            break;
                        }
                        final MessageProcessorType messageProcessorType = getMessageProcessorType(xmlTag);
                        if (messageProcessorType == MessageProcessorType.MESSAGE_PROCESSOR || messageProcessorType == MessageProcessorType.CONDITIONAL_ROUTE) {
                            index = index + 1;
                        }
                    }
                    path = path + "/" + index;
                }
            }
        }
        System.out.println("path = " + path);
        return path;
    }

    @Nullable
    public static MessageProcessorType getMessageProcessorType(XmlTag xmlTag) {
        final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
        if (descriptor instanceof XmlElementDescriptorImpl) {
            final XmlElementDescriptorImpl xmlElementDescriptor = (XmlElementDescriptorImpl) descriptor;
            final TypeDescriptor schemaType = xmlElementDescriptor.getType();
            if (schemaType instanceof ComplexTypeDescriptor) {
                final XmlTag complexTypeTag = ((ComplexTypeDescriptor) schemaType).getDeclaration();
                final MessageProcessorType typeReference = resolveMuleType(complexTypeTag);
                if (typeReference != null) {
                    return typeReference;
                }
            }
        }
        return null;
    }

    @Nullable
    private static MessageProcessorType resolveMuleType(XmlTag complexTypeTag) {
        final PsiReference baseType = getBaseType(complexTypeTag);
        if (baseType != null) {
            final PsiElement resolve = baseType.resolve();
            if (resolve instanceof XmlTag) {
                final XmlTag typeReference = (XmlTag) resolve;
                final String name = typeReference.getAttributeValue("name");
                if (name != null && !name.isEmpty()) {
                    final MessageProcessorType[] messageProcessorTypes = MessageProcessorType.values();
                    for (MessageProcessorType messageProcessorType : messageProcessorTypes) {
                        if (messageProcessorType.isValidType(name)) {
                            return messageProcessorType;
                        }
                    }
                    return resolveMuleType(typeReference);
                }
            }
        }
        return null;
    }


    @Nullable
    private static PsiReference getBaseType(XmlTag complexTypeTag) {
        final XmlTag complexContent = getComplexContents(complexTypeTag);
        if (complexContent != null) {
            final XmlTag extension = getExtensions(complexContent);
            if (extension != null) {
                final XmlAttribute base = extension.getAttribute("base");
                if (base != null && base.getValueElement() != null) {
                    return SchemaReferencesProvider.createTypeOrElementOrAttributeReference(base.getValueElement());
                }
            }
        }
        return null;
    }

    @Nullable
    private static XmlTag getExtensions(XmlTag complexContent) {
        final XmlTag[] complexContents = complexContent.findSubTags("extension", "http://www.w3.org/2001/XMLSchema");
        return complexContents.length > 0 ? complexContents[0] : null;
    }

    @Nullable
    private static XmlTag getComplexContents(XmlTag complexTypeTag) {
        final XmlTag[] complexContents = complexTypeTag.findSubTags("complexContent", "http://www.w3.org/2001/XMLSchema");
        return complexContents.length > 0 ? complexContents[0] : null;
    }

    @NotNull
    private static String getGlobalElementCategory(XmlTag element) {
        switch (element.getLocalName()) {
            case "flow":
                return "/processors";
            case "sub-flow":
                return "/subprocessors";
            default:
                return "/es";
        }

    }

    @NotNull
    public static String asMelScript(@NotNull String script) {
        return !script.startsWith("#[") ? "#[" + script + "]" : script;
    }

    public enum MessageProcessorType {
        MESSAGE_SOURCE("abstractMessageSourceType"), MESSAGE_PROCESSOR("abstractMessageProcessorType"), CONDITIONAL_ROUTE("abstractMessageProcessorFilterPairType");

        private String[] validTypes;

        MessageProcessorType(String... validTypes) {

            this.validTypes = validTypes;
        }

        public boolean isValidType(String type) {
            return Arrays.asList(validTypes).contains(type);
        }
    }
}