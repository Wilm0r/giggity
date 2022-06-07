# Giggity

Giggity is an Android app that loads
[Pentabarf](https://github.com/nevs/pentabarf)/[frab](https://github.com/frab/frab)/[wafer](https://github.com/CTPUG/wafer)/[Pretalx](https://github.com/pretalx/pretalx)
XML files (or if none are available, .ics) with schedules of conferences
and other events, and displays them in various convenient formats.

Nowadays many events release dedicated apps (with limited features) which I
don't like. :-( Giggity is a generic app that can be used for any event that
publishes their schedule in an open format, and offers more features to help
with organising your visit, like:

 * Various different views besides a plain timetable.
 * Powerful search function.
 * Set reminders.
 * Warnings when your selected talks overlap.
 * Delete/hide talks/entire topics you're not interested in.
 * Events can include direct links to related web content (main website,
   Wiki), or include off-line viewable venue maps, etc.
 * Include coordinates of all the venue's rooms so you can see where
   they are in your preferred maps application, or c3nav integration.
 * Add direct shortcut (or widget) to your homescreen.
 * Export your selections via a QR code to sync with other devices or with your
   friends. (Relies on deprecated ZXing QR scanner, functionality to be
   replaced.

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

## Deeplinking into Giggity

Giggity reads generic .ics or even .xml files and will not register
itself as a reader for these file formats with Android.

If you however want to add deeplinks on your website, prompting Android
phones to open your schedule file directly in Giggity, you can use
ggt.gaa.st URLs formatted like this:

```
https://ggt.gaa.st/#url=https://fosdem.org/2019/schedule/xml
```

Starting with Giggity 2.0, you can also include your JSON metadata in these
URLs if you have any. Use [this script](tools/ggt.sh) to generate the
right (and backward compatible) URL.

This is likely a better option than scanning a QR now that good ad-free
QR scanners appear to be rare. But where possible, consider just adding
your event to the menu as explained in the next section.

## Adding your event to the default menu

To do this, construct a JSON file formatted like this in the [menu directory](menu):

```js
{
	"version": 2019122000,
	"url": "URL_OF_YOUR_PENTABARF_FILE",
	"title": "TITLE",  // preferably have it match the title in your Pentabarf
	"start": "2020-02-01",
	"end": "2020-02-02",
	"timezone": "Europe/Brussels",
	// To be used only if your event tends to make last-minute changes, and allowed only if the
	// server hosting your Pentabarf file sends HTTP 304s when no changes are made:
	"refresh_interval": 1800,
	"metadata": {
		// Must have an alpha layer, be square and not too large. Will be used for
		// notifications and home shortcut.
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
		// Entirely optional:
		"rooms": [
			{
				"name": "ROOM 1",  // Warning: it's a regex!
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
maps with for example Google). Note that the room name is actually a
regular expression (which could be used to combine entries for adjacent
rooms for example).

For those conferences using [c3nav](https://github.com/c3nav/c3nav), the
[FOSDEM fragment](menu/fosdem_2019.json) shows how to integrate that.
In case of c3nav you'll likely just want to have an entry for every
room instead of taking advantage of regex matching.

To test your entry, you can for example turn it into a QR-encode
using previously mentioned [tools/ggt.sh](tools/ggt.sh) and `python3-qr`:

```sh
tools/ggt.sh menu/YOURFILE.json | qr
```

(`sudo apt-get install python3-qrcode` if it doesn't work, or use any other
encoder that you may know of.)

And use a QR scanner (for example Lens in the Camera application) to open it
on your phone.

To get your entry added to Giggity, just add it to the [menu directory](menu) and
send a pull request. To save time, run `tools/menu-ci.py` for sanity checking. 
(Same check is run automatically through Travis-CI.)
