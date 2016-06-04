/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

public interface ScheduleViewer {
	/* These two maybe should've just been the same. */
	/* Called every minute to update clock/marking of currently happening events. */
	public void refreshContents();
	/* Called after possible event state changes (ScheduleItemActivity or more tricky on tablets) */
	public void refreshItems();

	/* Allow date switching. Does not apply to now&next for example. */
	public boolean multiDay();

	/* Currently used by TimeTable only to blend the room tabs with the action bar. */
	public boolean extendsActionBar();
}
