"""Native desktop controls for independent Luna grid layers. Author: lare96."""
import tkinter as tk
from tkinter import ttk
import json

from grids import grid_at, grid_cells, grid_lines, clean_custom_labels


class GridSupport:
    """Small AtlasApp mixin; preferences modify Atlas display only.

    @author lare96
    """

    def init_grid_support(self):
        self.region_grid_lines=getattr(self,'grid',None) or tk.BooleanVar(value=False)
        self.grid=self.region_grid_lines
        self.region_grid_labels=tk.BooleanVar(value=False)
        self.chunk_grid_lines=tk.BooleanVar(value=False)
        self.chunk_grid_labels=tk.BooleanVar(value=False)
        self.custom_region_labels={}
        self.custom_chunk_labels={}
        self.grid_selection=None
        self.grid_dialog=None
        self.grid_info=tk.StringVar(value='Select a region or chunk to inspect its native coordinates and bounds.')
        self.grid_custom_name=tk.StringVar(value='')

    def build_grid_toolbar(self,parent):
        button=ttk.Menubutton(parent,text='Grids ▾')
        menu=tk.Menu(button,tearoff=False,bg='#18252d',fg='#e7e4d7',activebackground='#304b56',activeforeground='#ffffff')
        for title,variable in (('Region boundaries',self.region_grid_lines),('Region labels',self.region_grid_labels),
                               ('Chunk boundaries',self.chunk_grid_lines),('Chunk labels',self.chunk_grid_labels)):
            menu.add_checkbutton(label=title,variable=variable,command=self.grid_display_changed)
        menu.add_separator()
        menu.add_command(label='Select a region on the map',command=lambda:self.pick_grid_mode('region'))
        menu.add_command(label='Select a chunk on the map',command=lambda:self.pick_grid_mode('chunk'))
        menu.add_separator()
        menu.add_command(label='Grid details and custom labels…',command=self.open_grid_dialog)
        button.configure(menu=menu)
        button.pack(side='left',padx=(5,10))
        return button

    def grid_display_changed(self):
        self.schedule_draw()
        if hasattr(self,'queue_preferences'):
            self.queue_preferences()

    def pick_grid_mode(self,kind):
        self.mode.set(kind)
        if hasattr(self,'cancel_rectangle'):
            self.cancel_rectangle()
        self.status.set('Click a map tile to inspect its '+kind+'. Display labels do not change Luna data.')

    def grid_preferences(self):
        return {'regionLines':bool(self.region_grid_lines.get()),'regionLabels':bool(self.region_grid_labels.get()),
                'chunkLines':bool(self.chunk_grid_lines.get()),'chunkLabels':bool(self.chunk_grid_labels.get()),
                'customRegionLabels':dict(self.custom_region_labels),'customChunkLabels':dict(self.custom_chunk_labels)}

    def apply_grid_preferences(self,preferences,legacyGrid=None):
        preferences=preferences if isinstance(preferences,dict) else {}
        for key,variable in (('regionLines',self.region_grid_lines),('regionLabels',self.region_grid_labels),
                             ('chunkLines',self.chunk_grid_lines),('chunkLabels',self.chunk_grid_labels)):
            value=preferences.get(key,legacyGrid if key=='regionLines' and isinstance(legacyGrid,bool) else False)
            variable.set(value if isinstance(value,bool) else False)
        self.custom_region_labels=clean_custom_labels(preferences.get('customRegionLabels'),'region')
        self.custom_chunk_labels=clean_custom_labels(preferences.get('customChunkLabels'),'chunk')
        self.grid_selection=None
        self.grid_info.set('Select a region or chunk to inspect its native coordinates and bounds.')
        self.grid_custom_name.set('')

    def _grid_name(self,info):
        return (self.custom_region_labels if info['kind']=='region' else self.custom_chunk_labels).get(info['key'],'')

    def select_grid_at(self,x,y,kind='region'):
        try:
            info=grid_at(x,y,int(self.plane.get()),kind)
        except ValueError as error:
            self.status.set(str(error))
            return None
        self.grid_selection=info
        self.refresh_grid_plane()
        self.schedule_draw()
        self.open_grid_dialog()
        return info

    def refresh_grid_plane(self):
        info=self.grid_selection
        if not info:
            return
        b=info['bounds']
        info=grid_at(b['minX'],b['minY'],int(self.plane.get()),info['kind'])
        self.grid_selection=info
        name=self._grid_name(info)
        known=(info['regionX'],info['regionY']) in getattr(self,'regions',set())
        lines=[info['identifier'],f"Plane {info['plane']} · {info['size']} × {info['size']} tiles",
               f"Inclusive X {b['minX']}…{b['maxX']} · Y {b['minY']}…{b['maxY']}",
               f"Region ID {info['regionId']} · coordinates {info['regionX']}, {info['regionY']}",
               'Selected cache region: '+('present' if known else 'not loaded/absent')]
        if info['kind']=='chunk':
            lines.extend([f"Global chunk {info['worldChunkX']}, {info['worldChunkY']}",
                          f"Luna Chunk.x/y {info['nativeX']}, {info['nativeY']} (global − 6)",
                          f"Within region {info['localChunkX']}, {info['localChunkY']} (0–7)",
                          f"Packed native coordinate {info['packedCoordinate']} · mixed hash {info['hashCode']}",
                          'The mixed hash is not a coordinate identifier.'])
        lines.extend(['','Custom display label: '+(name or '(none)'),
                      'Custom labels apply to this coordinate cell across planes and remain in Atlas settings.',
                      'Source: '+info['source']])
        self.grid_info.set('\n'.join(lines))
        self.grid_custom_name.set(name)

    def save_grid_label(self):
        info=self.grid_selection
        if not info:
            self.status.set('Select a region or chunk before assigning a display label.')
            return
        name=' '.join(self.grid_custom_name.get().split())
        if len(name)>80:
            self.status.set('Display labels are limited to 80 characters.')
            return
        labels=self.custom_region_labels if info['kind']=='region' else self.custom_chunk_labels
        previous=labels.get(info['key'])
        if name:
            if info['key'] not in labels and len(labels)>=4096:
                self.status.set('The project already has the maximum 4096 custom labels for this grid.')
                return
            labels[info['key']]=name
        else:
            labels.pop(info['key'],None)
        if len(json.dumps(self.grid_preferences()))>60_000:
            if previous is None:
                labels.pop(info['key'],None)
            else:
                labels[info['key']]=previous
            self.status.set('Custom grid labels reached the project display-settings budget. Shorten or remove an existing label first.')
            return
        self.refresh_grid_plane()
        self.grid_display_changed()
        self.status.set('Atlas display label saved. Luna map data and identifiers are unchanged.')

    def clear_grid_selection(self):
        self.grid_selection=None
        self.grid_info.set('Select a region or chunk to inspect its native coordinates and bounds.')
        self.grid_custom_name.set('')
        self.schedule_draw()

    def open_grid_dialog(self):
        if self.grid_dialog is not None:
            try:
                if self.grid_dialog.winfo_exists():
                    self.grid_dialog.lift()
                    return
            except tk.TclError:
                pass
        dialog=self.grid_dialog=tk.Toplevel(self.root)
        dialog.title('Luna grids · Display layers and cell details')
        dialog.geometry('610x600')
        dialog.minsize(510,520)
        dialog.transient(self.root)
        frame=ttk.Frame(dialog,padding=16)
        frame.pack(fill='both',expand=True)
        ttk.Label(frame,text='Regions and chunks',style='Title.TLabel').pack(anchor='w')
        ttk.Label(frame,text='64 × 64 regions · 8 × 8 chunks. All controls affect Atlas only.',wraplength=550).pack(anchor='w',pady=(7,10))
        toggles=ttk.Frame(frame)
        toggles.pack(fill='x')
        for column,(title,variable) in enumerate((('Region boundaries',self.region_grid_lines),('Region labels',self.region_grid_labels),
                                                 ('Chunk boundaries',self.chunk_grid_lines),('Chunk labels',self.chunk_grid_labels))):
            ttk.Checkbutton(toggles,text=title,variable=variable,command=self.grid_display_changed).grid(row=column//2,column=column%2,sticky='w',padx=(0,22),pady=3)
        ttk.Label(frame,text='Chunk lines appear at 2 px/tile; chunk labels at 8 px/tile. Region labels appear at 2 px/tile.',wraplength=550,style='Muted.TLabel').pack(anchor='w',pady=(8,12))
        picks=ttk.Frame(frame);picks.pack(fill='x')
        ttk.Button(picks,text='Pick region',command=lambda:self.pick_grid_mode('region')).pack(side='left')
        ttk.Button(picks,text='Pick chunk',command=lambda:self.pick_grid_mode('chunk')).pack(side='left',padx=7)
        ttk.Button(picks,text='Clear selection',command=self.clear_grid_selection).pack(side='left')
        ttk.Label(frame,textvariable=self.grid_info,justify='left',wraplength=550).pack(fill='both',expand=True,pady=(14,10))
        edit=ttk.Frame(frame);edit.pack(fill='x')
        ttk.Label(edit,text='Display label').pack(side='left')
        ttk.Entry(edit,textvariable=self.grid_custom_name).pack(side='left',fill='x',expand=True,padx=8)
        ttk.Button(edit,text='Save',command=self.save_grid_label).pack(side='left')
        ttk.Label(frame,text='Leave the label empty and Save to return to the native ID/coordinates.',style='Muted.TLabel',wraplength=550).pack(anchor='w',pady=(7,10))
        ttk.Button(frame,text='Close',command=dialog.destroy).pack(anchor='e')

    def draw_grids(self):
        canvas=self.canvas
        canvas.delete('grid')
        bounds=self.view_bounds()
        plane=int(self.plane.get())
        scale=float(self.scale)
        if self.chunk_grid_lines.get() and scale>=2:
            for x0,y0,x1,y1 in grid_lines(bounds,'chunk'):
                a,b=self.screen(x0,y0);c,d=self.screen(x1,y1)
                canvas.create_line(a,b,c,d,fill='#536f78',width=1,dash=(2,4),tags='grid')
        if self.region_grid_lines.get():
            for x0,y0,x1,y1 in grid_lines(bounds,'region'):
                a,b=self.screen(x0,y0);c,d=self.screen(x1,y1)
                canvas.create_line(a,b,c,d,fill='#b8d3da',width=2 if scale>=2 else 1,tags='grid')
        occupied=[]
        for kind,enabled,threshold in (('region',self.region_grid_labels.get(),2),('chunk',self.chunk_grid_labels.get(),8)):
            if not enabled or scale<threshold:
                continue
            for info in grid_cells(bounds,kind,plane,limit=2048):
                if (info['regionX'],info['regionY']) not in getattr(self,'regions',set()):
                    continue
                b=info['bounds']
                label=self._grid_name(info)
                native=f"R{info['regionId']}" if kind=='region' else f"{info['x']},{info['y']}"
                width=info['size']*scale
                maximum=max(5,int(width/5)-2)
                text=(label[:maximum-1]+'…' if len(label)>maximum else label)+'\n'+native if label else native
                x,y=self.screen((b['minX']+b['maxX']+1)/2,(b['minY']+b['maxY']+1)/2)
                font=('Consolas',9 if kind=='region' else 8,'bold' if kind=='region' else 'normal')
                item=canvas.create_text(x,y,text=text,fill='#eef4dc' if kind=='region' else '#bad0d4',font=font,tags=('grid','gridlabel'))
                box=canvas.bbox(item)
                if box is None or any(box[0]<b[2]+5 and box[2]>b[0]-5 and box[1]<b[3]+4 and box[3]>b[1]-4 for b in occupied):
                    canvas.delete(item);continue
                occupied.append(box)
                for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)):
                    shadow=canvas.create_text(x+dx,y+dy,text=text,fill='#101a20',font=font,tags=('grid','gridlabel'))
                    canvas.tag_lower(shadow,item)
        if self.grid_selection:
            if self.grid_selection['plane']!=plane:
                self.refresh_grid_plane()
            b=self.grid_selection['bounds']
            x0,y0=self.screen(b['minX'],b['maxY']+1);x1,y1=self.screen(b['maxX']+1,b['minY'])
            canvas.create_rectangle(x0,y0,x1,y1,outline='#fff1a4',width=3,dash=(6,3),tags='grid')
