package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerJson.ObjectValue;

/** Typed editing identities with preserved, potentially incomplete definition source. */
public record ResearchDesignerDraft(Resource profile, Resource tree, List<Node> nodes) {
    public ResearchDesignerDraft { Objects.requireNonNull(profile); Objects.requireNonNull(tree); nodes = List.copyOf(nodes); }
    public record Resource(String id, ObjectValue definition) {
        public Resource { ResearchDesignerProject.resourceId(id); Objects.requireNonNull(definition); }
    }
    public record Requirement(String id, List<String> anyOf) {
        public Requirement {
            ResearchDesignerProject.editorId(id); anyOf = List.copyOf(anyOf);
            anyOf.forEach(ResearchDesignerProject::resourceId);
        }
    }
    public record Node(String id, Optional<String> blueprintId, ObjectValue placement,
                       Optional<String> branchId, ObjectValue policy, List<Requirement> requirements) {
        public Node {
            ResearchDesignerProject.editorId(id); blueprintId = Objects.requireNonNull(blueprintId);
            blueprintId.ifPresent(ResearchDesignerProject::resourceId);
            branchId = Objects.requireNonNull(branchId); branchId.ifPresent(ResearchDesignerProject::resourceId);
            Objects.requireNonNull(placement); Objects.requireNonNull(policy); requirements = List.copyOf(requirements);
        }
    }
}
