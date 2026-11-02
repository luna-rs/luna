"""Standalone desktop startup with a cache-selection splash. Author: lare96."""
import json
from pathlib import Path
import queue
import sys
import threading
import traceback
import tkinter as tk
from tkinter import filedialog, messagebox

from atlas import AtlasServer, ROOT, atomic_json, load_selected


def main():
    state=ROOT/'.local'
    state.mkdir(exist_ok=True)
    log=(state/'desktop.log').open('a',encoding='utf-8',buffering=1)
    sys.stdout=log
    sys.stderr=log
    splash=tk.Tk()
    splash.title('Luna Atlas 377')
    splash.configure(bg='#111a20')
    splash.geometry('510x190')
    splash.resizable(False,False)
    tk.Label(splash,text='LUNA ATLAS',font=('Segoe UI',24,'bold'),fg='#d5b56d',bg='#111a20').pack(pady=(25,6))
    label=tk.Label(splash,text='Opening your Luna workspace…',font=('Segoe UI',12),fg='#e7e4d7',bg='#111a20')
    label.pack()
    tk.Label(splash,text='Source cache stays unchanged. Areas are saved as separate drafts.',
             fg='#9aadb5',bg='#111a20',font=('Segoe UI',9)).pack(pady=12)
    try:
        results=queue.Queue()
        def load():
            try:
                atlas=load_selected(state,welcome=True)
                if atlas.world:
                    atomic_json(state/'project.json',atlas.saved_project())
                results.put((atlas,None))
            except Exception as error:
                traceback.print_exc(file=log)
                results.put((None,str(error)))
        threading.Thread(target=load,daemon=True).start()
        loaded=[]
        def poll():
            try:
                atlas,error=results.get_nowait()
            except queue.Empty:
                splash.after(100,poll)
                return
            if error:
                messagebox.showerror('Cannot open this Luna directory',error,parent=splash)
            else:
                loaded.append(atlas)
            splash.destroy()
        splash.after(100,poll)
        splash.mainloop()
        if not loaded:
            return
        server=AtlasServer(loaded[0],0)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        try:
            import desktop
            desktop.main(f'http://127.0.0.1:{server.server_port}')
        finally:
            server.shutdown()
            server.server_close()
    except Exception as error:
        traceback.print_exc(file=log)
        messagebox.showerror('Luna Atlas',str(error)+'\nDetails: '+str(state/'desktop.log'))
    finally:
        log.close()


if __name__=='__main__':
    main()
