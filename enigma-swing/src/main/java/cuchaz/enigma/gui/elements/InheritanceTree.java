package cuchaz.enigma.gui.elements;

import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;

import cuchaz.enigma.gui.Gui;
import cuchaz.enigma.gui.renderer.InheritanceTreeCellRenderer;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

public class InheritanceTree extends AbstractInheritanceTree {
	public InheritanceTree(Gui gui) {
		super(gui, new InheritanceTreeCellRenderer(gui));
	}

	@Nullable
	@Override
	protected DefaultMutableTreeNode getNodeFor(Entry<?> entry) {
		if (entry instanceof ClassEntry) {
			return this.gui.getController().getClassInheritance((ClassEntry) entry);
		} else if (entry instanceof MethodEntry) {
			return this.gui.getController().getMethodInheritance((MethodEntry) entry);
		}

		return null;
	}

	@Override
	protected String getPanelName() {
		return I18n.translate("info_panel.tree.inheritance");
	}
}
