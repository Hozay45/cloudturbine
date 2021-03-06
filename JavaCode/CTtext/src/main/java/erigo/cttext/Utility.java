/*
Copyright 2017 Erigo Technologies LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package erigo.cttext;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

/**
 *
 * Class containing static utility methods.
 *
 */
public class Utility {

	/**************************************************************************
	 * 
	 * add()
	 * 
	 * Add a component to a container in the GUI using the GridBagLayout manager.
	 * 
	 * This code is from open source DataTurbine class com.rbnb.utility.Utility.
     * Covered by Apache 2.0 license.
     * Copyright 1997, 2000 Creare Inc, Hanover, N.H.
     * All rights reserved.
	 * 
	 * @author John P. Wilson
	 *
	 * @param container
	 *            Container to add Component to
	 * @param c
	 *            Component to add to the Container
	 * @param gbl
	 *            GridBagLayout manager to use
	 * @param gbc
	 *            GridBagConstrains to use to add ther Component
	 * @param x
	 *            Desired row position of the Component
	 * @param y
	 *            Desired column position of the Component
	 * @param w
	 *            Num of columns (width) Component should occupy
	 * @param h
	 *            Num of rows (height) Component should occupy
	 *
	 * @version 10/05/2006
	 */

	public static void add(Container container, Component c, GridBagLayout gbl, GridBagConstraints gbc, int x, int y, int w, int h) {
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.gridwidth = w;
		gbc.gridheight = h;
		gbl.setConstraints(c, gbc);
		container.add(c);
	}
	
}
