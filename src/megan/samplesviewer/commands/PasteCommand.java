/*
 *  Copyright (C) 2018 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.samplesviewer.commands;

import javafx.application.Platform;
import jloda.gui.commands.ICommand;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.commands.clipboard.ClipboardBase;
import megan.samplesviewer.SamplesViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class PasteCommand extends ClipboardBase implements ICommand {

    public String getSyntax() {
        return null;
    }

    public void apply(NexusStreamParser np) throws Exception {
    }

    public void actionPerformed(ActionEvent event) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                SamplesViewer samplesViewer = (SamplesViewer) getViewer();
                samplesViewer.getSamplesTable().pasteClipboard();
                samplesViewer.getSamplesTable().getDataGrid().save(samplesViewer.getSampleAttributeTable(), null);
                samplesViewer.getCommandManager().updateEnableStateFXItems();
                if (!samplesViewer.getDocument().isDirty() && samplesViewer.getSamplesTable().getDataGrid().isChanged(samplesViewer.getSampleAttributeTable())) {
                    samplesViewer.getDocument().setDirty(true);
                    samplesViewer.setWindowTitle();
                }
            }
        });
    }

    public boolean isApplicable() {
        return getViewer() instanceof SamplesViewer && ((SamplesViewer) getViewer()).getSamplesTable().getNumberOfSelectedCols() > 0;
    }

    public static final String ALT_NAME = "Samples Viewer Paste";

    public String getAltName() {
        return ALT_NAME;
    }

    public String getName() {
        return "Paste";
    }

    public String getDescription() {
        return "Paste";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("sun/toolbarButtonGraphics/general/Paste16.gif");
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }
}

