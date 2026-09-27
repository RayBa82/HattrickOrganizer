package core.gui.model;

import core.gui.comp.renderer.HODefaultTableCellRenderer;
import core.gui.theme.HOColorName;
import core.gui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;

public class MatchOrdersRenderer implements javax.swing.ListCellRenderer<MatchOrdersCBItem>{

    @Override
    public Component getListCellRendererComponent(JList<? extends MatchOrdersCBItem> list, MatchOrdersCBItem value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value != null) {
            Component comp = value.getListCellRendererComponent(isSelected);
            // room between the edge, the match type icon and the text
            if (comp instanceof JComponent jc) jc.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            if (isSelected) {
                comp.setBackground(HODefaultTableCellRenderer.SELECTION_BG);
            } else {
                comp.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));
            }
            return comp;
        } else {
            JLabel m_jlBlank = new JLabel(" ");
            m_jlBlank.setOpaque(true);
            if (isSelected) {
                m_jlBlank.setBackground(HODefaultTableCellRenderer.SELECTION_BG);
            } else {
                m_jlBlank.setBackground(ThemeManager.getColor(HOColorName.BACKGROUND_CONTAINER));
            }
            return m_jlBlank;
        }
    }

}
