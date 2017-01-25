# Giggity

Giggity is an Android app that loads
xcal/[Pentabarf](http://www.pentabarf.org/)/[frab](https://github.com/frab/frab)/[wafer](https://github.com/CTPUG/wafer)
XML files (that contain schedules of conferences/festivals/other events)
and lets you browse them in various convenient formats.

Nowadays many events release dedicated apps (sometimes with limited
features) which I don't like. :-( With Giggity I try to just offer a
generic app that can be used for any event that publishes their schedule
in an open format, and offer more features to help with organising your
visit, like:

 * Various different views besides a plain timetable.
 * Search function so you can search for the exact topics you care
   about.
 * Set reminders.
 * Get a warning when talks overlap with other talks you're going to.
 * Delete/hide talks/entire topics you're not interested in.
 * Export your selections via a QR code to sync with other devices or
   with your friends.
 * Events can include direct links to related web content (main website,
   Wiki), or include off-line viewable venue maps, etc.
 * Include coordinates of all the venue's rooms so you can see where
   they are in your preferred maps application.
 * Add direct shortcuts or a widget (always showing the next talk) to
   your homescreen.

It's free software, and available on [Google
Play](https://play.google.com/store/apps/details?id=net.gaast.giggity&hl=en),
[F-Droid](https://f-droid.org/repository/browse/?fdid=net.gaast.giggity)
and I guess other Android markets.

It's named Giggity, after the word "Gig". The fact that it is also a
well-known catch phrase of a certain cartoon character may or may not be
coincidental. ;-)

<a href="https://f-droid.org/app/net.gaast.giggity">
<img src="https://f-droid.org/badge/get-it-on.png" height="64" alt="F-Droid">
</a>
<a href="https://play.google.com/store/apps/details?id=net.gaast.giggity">
<img src="https://play.google.com/intl/en_gb/badges/images/generic/en_badge_web_generic.png" height="64" alt="Play Store">
</a>

## Using Giggity for your event

You can add any suitably formatted schedule to Giggity yourself (use QR
codes to make this easier), or it can be added to Giggity's main menu.

To do this, e-mail me a JSON file formatted like this:

```json
	{
		"version": 2016080500,
		"url": "URL_OF_YOUR_PENTABARF_FILE",
		"title": "TITLE (preferably have it match the title in your Pentabarf",
		"start": "2016-08-10",
		"end": "2016-08-12",
		"metadata": {
			// Must have an alpha layer, be square and not too large.
			// Will be used for notifications and home shortcut.
			"icon": "https://www.conference.org/logo.png",
			"links": [
				{
					"url": "https://www.conference.org/",
					"title": "Website"
				},
				{
					"url": "https://www.conference.org/info.pdf",
					"title": "Info",
					"type": "application/pdf"
				},
				{
					"url": "https://www.conference.org/floorplan.png",
					"title": "Map",
					"type": "image/png"
				}
			],
			"rooms": [
				{
					"name": "ROOM 1",
					"latlon": [51.482598, -0.144742]
				},
				{
					"name": "ROOM 2",
					"latlon": [51.481024, -0.145571]
				}
			]
		}
	}
```

The `metadata` section (and/or its two subsections) is optional but
recommended as it lets you define links to show automatically in
Giggity's nav drawer when viewing your event. Adding a MIME-type to a
link will make Giggity download that file and show it off-line instead
of in the browser, great for slow conference WiFi. Feel free to add other
kinds of links as you see fit.

Less commonly used: Adding room locations will make room names in event
description clickable, sending the user to the given latlon in their
preferred maps application (especially great if your venue has indoor
maps with for example Google).  Note that the room name is actually a
regular expression (which could be used to combine entries for adjacent
rooms for example).

To test your entry, QR-encode it using for example this command:

```sh
gzip -9 < YOURFILE.json | python2-qr --optimize=0
```

(`sudo apt-get install python-qrcode` if it doesn't work, or at your own
risk use a different encoder. The `--optimize=0` bit is to disable some
optimisation code that corrupts binary data. Or if your entry is small
and your phone camera good, just leave out the gzip.)

Then scan the code from the Giggity main menu (+ on the top-right, then
"SCAN QR").

## Repository setup

This is an Android Studio project. Good luck getting your local install
to import it properly! It's a process that can either be enjoyable or
make me miss Makefiles, though it appears much less fragile than it was
with Eclipse?

Sadly Eclipse and Android Studio have done a great job at fscking up the
indentation (meant to be tabs but got mixed here and there). Maybe I'll
decrustify some day.. Please do use tabs.
