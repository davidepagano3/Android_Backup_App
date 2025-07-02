from datetime import datetime, date, timedelta
from pathlib import Path
import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, Gdk

namelist = ["giuseppe", "barbara", "davide", "nicolo"]

class MyWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="Hello World")
        button_color = {}
        self.Button = {}
        self.set_border_width(10)

        self.box = Gtk.Box(spacing=6)
        self.add(self.box)

        for name in namelist:
           date_file = Path("/storage/%s/Last_Backup_File.txt" % name)
           yesterday = date.today() - timedelta(days=1)
           lastweek = date.today() - timedelta(days=5)
           button_color[name] = "Red-Button"
           if date_file.is_file():
             f = date_file.open('r')
             l_bck_date_str = f.readline()
             l_bck_date_lst = l_bck_date_str.split("/")
             if len(l_bck_date_lst) == 3:
                d = int(l_bck_date_lst[0])
                m = int(l_bck_date_lst[1])
                y = int(l_bck_date_lst[2])
                l_bck_date = date(y, m, d)
                if (l_bck_date >= lastweek):
                    button_color[name] = "Orange-Button"
                if (l_bck_date >= yesterday):
                    button_color[name] = "Green-Button"

        self.Button["G"] = Gtk.Button(label="PAPA'")
        self.set_button_color(self.Button["G"], button_color['giuseppe'])
        self.Button["G"].connect("clicked", self.on_buttonG_clicked)
        self.box.pack_start(self.Button["G"], True, True, 0)

        self.Button["B"] = Gtk.Button(label="MAMMA")
        self.set_button_color(self.Button["B"], button_color['barbara'])
        self.Button["B"].connect("clicked", self.on_buttonB_clicked)
        self.box.pack_start(self.Button["B"], True, True, 0)

        self.Button["D"] = Gtk.Button(label="DAVIDE")
        self.set_button_color(self.Button["D"], button_color['davide'])
        self.Button["D"].connect("clicked", self.on_buttonD_clicked)
        self.box.pack_start(self.Button["D"], True, True, 0)

        self.Button["N"] = Gtk.Button(label="NICOLO")
        self.set_button_color(self.Button["N"], button_color['nicolo'])
        self.Button["N"].connect("clicked", self.on_buttonN_clicked)
        self.box.pack_start(self.Button["N"], True, True, 0)
        self.apply_css()

    def set_button_color(self, w_button, color):
        w_button.set_name(color)

    def on_buttonG_clicked(self, widget):
        print("Hello")

    def on_buttonB_clicked(self, widget):
        print("Goodbye")

    def on_buttonD_clicked(self, widget):
        print("Hello")

    def on_buttonN_clicked(self, widget):
        print("Goodbye")


    def apply_css(self):
        css = b"""
        #Red-Button {
            background-color: red;
            color: white;
        }
        #Orange-Button {
            background-color: orange;
            color: white;
        }
        #Green-Button {
            background-color: green;
            color: white;
        }
        #ButtonN {
            background-color: red;
            color: white;
        }
        """

        style_provider = Gtk.CssProvider()
        style_provider.load_from_data(css)

        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(),
            style_provider,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )

win = MyWindow()
win.connect("destroy", Gtk.main_quit)
win.fullscreen()
win.show_all()
Gtk.main()
