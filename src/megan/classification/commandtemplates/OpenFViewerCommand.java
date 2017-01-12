/*
 *  Copyright (C) 2017 Daniel H. Huson
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
package megan.classification.commandtemplates;

import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.classification.ClassificationManager;
import megan.core.Director;
import megan.core.Document;
import megan.viewer.ClassificationViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;

/**
 * Command for opening a named function viewer
 * These commands are added to the global list of commands. They are not generated by reflection.
 * Daniel Huson, 4.2015
 */
public class OpenFViewerCommand extends CommandBase implements ICommand {
    private final String cName;

    /**
     * constructor
     *
     * @param cName
     */
    public OpenFViewerCommand(String cName) {
        this.cName = cName;
    }

    /**
     * parses the given command and executes it
     *
     * @param np
     * @throws java.io.IOException
     */
    @Override
    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase(getSyntax());

        ClassificationViewer classificationViewer = (ClassificationViewer) ((Director) getDir()).getViewerByClassName(cName);
        if (classificationViewer == null) {
            try {
                classificationViewer = new ClassificationViewer((Director) getDir(), ClassificationManager.get(cName, true), true);
                getDir().addViewer(classificationViewer);
            } catch (Exception e) {
                Basic.caught(e);
            }
        }
        if (classificationViewer != null) {
            classificationViewer.updateView(Director.ALL);
            classificationViewer.getFrame().setVisible(true);
            classificationViewer.getFrame().setState(JFrame.NORMAL);
            classificationViewer.getFrame().toFront();
        }
    }

    /**
     * get command-line usage description
     *
     * @return usage
     */
    @Override
    public String getSyntax() {
        return "open viewer=" + cName + ";";
    }

    /**
     * action to be performed
     *
     * @param ev
     */
    @Override
    public void actionPerformed(ActionEvent ev) {
        execute(getSyntax());
    }

    /**
     * get the name to be used as a menu label
     *
     * @return name
     */
    @Override
    public String getName() {
        return "Open " + cName + " Viewer...";
    }

    /**
     * get description to be used as a tooltip
     *
     * @return description
     */
    @Override
    public String getDescription() {
        return "Open " + cName + " viewer";
    }

    /**
     * get icon to be used in menu or button
     *
     * @return icon
     */
    @Override
    public ImageIcon getIcon() {
        final String iconFile = ClassificationManager.getIconFileName(cName);
        if (iconFile != null) {
            if (ResourceManager.getImageResource(ResourceManager.iconPackagePath, iconFile) == null && ResourceManager.getIconMap().get(iconFile) == null) { // no icon file found, build an icon....
                ResourceManager.getIconMap().put(iconFile, new MyImageIcon(iconFile));
            }
        }
        return iconFile == null ? null : ResourceManager.getIcon(iconFile);
    }

    /**
     * icon to represent classification
     */
    class MyImageIcon extends ImageIcon {
        /**
         * construct icon
         *
         * @param label
         */
        MyImageIcon(String label) {
            final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor((new JButton()).getBackground());
            g.fillRect(0, 0, 16, 16);
            g.setFont(new Font("Arial", Font.PLAIN, 15));
            g.setColor(Color.BLACK);
            g.drawString(label.substring(0, 1).toUpperCase(), 1, 13);
            g.dispose();
            setImage(image);
        }
    }

    /**
     * gets the accelerator key  to be used in menu
     *
     * @return accelerator key
     */
    @Override
    public KeyStroke getAcceleratorKey() {
        return null;
    }

    /**
     * is this a critical command that can only be executed when no other command is running?
     *
     * @return true, if critical
     */
    @Override
    public boolean isCritical() {
        return true;
    }

    /**
     * is the command currently applicable? Used to set enable state of command
     *
     * @return true, if command can be applied
     */
    @Override
    public boolean isApplicable() {
        if (ProgramProperties.get("always-allow-open-fviewer", false))
            return true;
        final Document doc = ((Director) getDir()).getDocument();
        return doc.getActiveViewers().contains(cName);
    }
}
