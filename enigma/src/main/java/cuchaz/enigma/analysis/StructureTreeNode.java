package cuchaz.enigma.analysis;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.api.service.NameProposalService;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.*;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StructureTreeNode extends DefaultMutableTreeNode {
    private final List<NameProposalService> nameProposalServices;
    private final EntryRemapper mapper;
    private final ClassEntry parentEntry;
    private final ParentedEntry entry;

    public StructureTreeNode(EnigmaProject project, ClassEntry parentEntry, ParentedEntry entry) {
        this.nameProposalServices = project.getEnigma().getServices().get(NameProposalService.TYPE);
        this.mapper = project.getMapper();
        this.parentEntry = parentEntry;
        this.entry = entry;
    }

    /**
     * Returns the parented entry represented by this tree node.
     */
    public ParentedEntry getEntry() {
        return this.entry;
    }

    public void load(EnigmaProject project, StructureTreeOptions options) {
        Stream<ParentedEntry> children = project.getJarIndex().getChildrenByClass().get(this.parentEntry).stream();

        switch (options.getObfuscationVisibility()) {
            case ALL: {
                break;
            }
            case OBFUSCATED: {
                // remove deobfuscated members if only obfuscated, unless it's an inner class
                children = children.filter(e -> (e instanceof ClassEntry) || (project.isObfuscated(e) && project.isRenamable(e)))
                // keep constructor methods if the class is obfuscated
                .filter(e -> !(e instanceof MethodEntry && ((MethodEntry) e).isConstructor()) || project.isObfuscated(e.getParent()));
                break;
            }
            case DEOBFUSCATED: {
                children = children.filter(e -> (e instanceof ClassEntry)
                        || (!project.isObfuscated(e) && project.isRenamable(e))
                        // keep constructor methods if the class is deobfuscated
                        || (e instanceof MethodEntry && ((MethodEntry) e).isConstructor()) && !project.isObfuscated(e.getParent()));
                break;
            }
        }

        switch (options.getDocumentationVisibility()) {
            case ALL: {
                break;
            }
            // TODO remove EntryRemapper.deobfuscate() calls when javadocs will no longer be tied to deobfuscation
            case DOCUMENTED: {
                children = children.filter(e -> (e instanceof ClassEntry) || (project.getMapper().deobfuscate(e).getJavadocs() != null && !project.getMapper().deobfuscate(e).getJavadocs().chars().allMatch(Character::isWhitespace)));
                break;
            }
            case NON_DOCUMENTED: {
                children = children.filter(e -> (e instanceof ClassEntry) || (project.getMapper().deobfuscate(e).getJavadocs() == null || project.getMapper().deobfuscate(e).getJavadocs().chars().allMatch(Character::isWhitespace)));
                break;
            }
        };

        switch (options.getSortingOrder()) {
            case DEFAULT: {
                break;
            }
            case A_Z: {
                children = children.sorted(Comparator.comparing(e -> (e instanceof MethodEntry && ((MethodEntry) e).isConstructor())
                        // compare the class name when the entry is a constructor
                        ? project.getMapper().deobfuscate(e.getParent()).getSimpleName().toLowerCase()
                        : project.getMapper().deobfuscate(e).getSimpleName().toLowerCase()));
                break;
            }
            case Z_A: {
                children = children.sorted(Comparator.comparing(e -> (e instanceof MethodEntry && ((MethodEntry) e).isConstructor())
                                ? project.getMapper().deobfuscate(((ParentedEntry<?>) e).getParent()).getSimpleName().toLowerCase()
                                : project.getMapper().deobfuscate((ParentedEntry<?>) e).getSimpleName().toLowerCase())
                        .reversed());
                break;
            }
        };

        for (ParentedEntry<?> child : children.collect(Collectors.toList())) {
            StructureTreeNode childNode = new StructureTreeNode(project, this.parentEntry, child);

            if (child instanceof ClassEntry) {
                childNode = new StructureTreeNode(project, (ClassEntry) child, child);
                childNode.load(project, options);
            }

            this.add(childNode);
        }
    }

    @Override
    public String toString() {
        TranslateResult<ParentedEntry> translateResult = this.mapper.extendedDeobfuscate(this.entry);
        String result = translateResult.getValue().getName();

        if (translateResult.isObfuscated()) {
            if (!this.nameProposalServices.isEmpty()) {
                for (NameProposalService service : this.nameProposalServices) {
                    if (service.proposeName(this.entry, this.mapper).isPresent()) {
                        result = service.proposeName(this.entry, this.mapper).get();
                    }
                }
            }
        }

        if (this.entry instanceof FieldDefEntry) {
            FieldDefEntry field = (FieldDefEntry) translateResult.getValue();
            String returnType = this.parseDesc(field.getDesc());

            result = result + ": " + returnType;
        } else if (this.entry instanceof MethodDefEntry) {
            MethodDefEntry method = (MethodDefEntry) translateResult.getValue();
            String args = this.parseArgs(method.getDesc().getArgumentDescs());
            String returnType = this.parseDesc(method.getDesc().getReturnDesc());

            if (method.isConstructor()) {
                result = method.getParent().getSimpleName() + args;
            } else {
                result = result + args + ": " + returnType;
            }
        }

        return result;
    }

    public String toHtml() {
        List<String> modifiers = new ArrayList<>();

        if (this.entry instanceof DefEntry<?>) {
            DefEntry<?> defEntry = (DefEntry<?>) this.entry;
            AccessFlags access = defEntry.getAccess();
            boolean isInterfaceMethod = false;

            if (this.entry instanceof MethodEntry && this.entry.getParent() instanceof ClassDefEntry) {
                ClassDefEntry parent = (ClassDefEntry) this.entry.getParent();
                isInterfaceMethod = parent.getAccess().isInterface();
            }

            if (access.isStatic() && !access.isEnum()) {
                // Static member, but not an enum constant
                modifiers.add("static");
            } else if (isInterfaceMethod && !access.isAbstract()) {
                // Non-static default interface method
                modifiers.add("default");
            }

            if (access.isAbstract() && !access.isInterface() && !isInterfaceMethod && !access.isEnum()) {
                // Abstract, but not an interface, an interface method or an enum class (abstract is the default or meaningless)
                modifiers.add("abstract");
            } else if (access.isFinal() && !access.isEnum()) {
                // Final, but not an enum or an enum constant (they're always final)
                modifiers.add("final");
            }
        }

        return "<i>" + String.join(" ", modifiers) + "</i> " + toString();
    }

    private String parseArgs(List<TypeDescriptor> args) {
        if (args.size() > 0) {
            String result = "(";

            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    result += ", ";
                }

                result += this.parseDesc(args.get(i));
            }

            return result + ")";
        }

        return "()";
    }

    private String parseDesc(TypeDescriptor desc) {
        if (desc.isVoid()) return "void";
        if (desc.isPrimitive()) return desc.getPrimitive().getKeyword();
        if (desc.isType()) return desc.getTypeEntry().getSimpleName();

        if (desc.isArray()) {
            if (desc.getArrayType().isPrimitive()) return desc.getArrayType().getPrimitive().getKeyword() + "[]";
            if (desc.getArrayType().isType()) return desc.getArrayType().getTypeEntry().getSimpleName() + "[]";
        }

        return null;
    }
}
