import sys, math, struct, time, json
from pathlib import Path
import serial
from serial.tools import list_ports
from PySide6.QtCore import Qt, QTimer
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWidgets import (QApplication,QMainWindow,QWidget,QVBoxLayout,QHBoxLayout,
 QLabel,QPushButton,QComboBox,QTabWidget,QGridLayout,QTableWidget,QTableWidgetItem,
 QFileDialog,QMessageBox,QGroupBox,QHeaderView,QStatusBar)

BAUD=8192; FRAME_LEN=60
RPM_ROWS=list(range(400,2201,200))+list(range(2400,8001,400))
VE_AXES=[list(map(float,[20,30,40,50,60,70,75,80,85,90,95,100])),
         list(map(float,range(100,301,20)))]
VE_ADDR=[0x6E06,0x6F35]; VE_SCALE=.390625; BIN_SIZE=65536

def decode_frame(f):
    u=lambda o:f[3+o]
    u16=lambda o:(u(o)<<8)|u(o+1)
    s16=lambda o:struct.unpack('>h',bytes([u(o),u(o+1)]))[0]
    baro=u(0x0A)*.369+10.354
    map_kpa=u(0x35)*1.22+2.2
    cmd=u(0x16)*.1
    wb=u(0x23)/10
    return dict(rpm=u16(0),coolant=u(5)*.75-40,baro=baro,map=map_kpa,
      boost=(map_kpa-baro)*.145038,tps=u(0x0D)*.390625,
      spark=s16(0x1F)*.351565,ve=u(0x22)/2.56,afr=wb,
      cmd_afr=cmd,knock=u(0x26)*.351565,knock_counts=u(0x19),
      speed=u(0x21),iat=None,voltage=None,iac=None)

def find_frame(buf):
    for i in range(max(0,len(buf)-FRAME_LEN+1)):
        f=bytes(buf[i:i+FRAME_LEN])
        if len(f)==FRAME_LEN and f[0]==0xF4 and f[1]==0x8E and f[2]==1 and sum(f)&255==0:
            return f,i+FRAME_LEN
    return None,max(0,len(buf)-FRAME_LEN+1)

class EnginePanel(QWebEngineView):
    def __init__(self):
        super().__init__()
        self.setMinimumSize(650,480)
        scene=Path(__file__).with_name('engine_scene.html')
        self.load(scene.as_uri())
    def update_data(self,d):
        payload=json.dumps({'rpm':d.get('rpm',0),'boost':d.get('boost',0)})
        self.page().runJavaScript('if(window.setEngineData) window.setEngineData('+payload+');')

class Gauge(QGroupBox):
    def __init__(self,title,unit,lo,hi):
        super().__init__(title); self.unit=unit; self.lo=lo; self.hi=hi; self.val=None
        self.label=QLabel('—'); self.label.setAlignment(Qt.AlignCenter)
        self.label.setStyleSheet('font-size:25px;font-weight:700;color:#e8f1f8;padding:8px')
        self.sub=QLabel(unit); self.sub.setAlignment(Qt.AlignCenter); self.sub.setStyleSheet('color:#8da8bd')
        l=QVBoxLayout(self); l.addWidget(self.label); l.addWidget(self.sub)
        self.setStyleSheet('QGroupBox{color:#9eb6c9;border:1px solid #294157;border-radius:8px;margin-top:8px;padding-top:8px}')
    def set_value(self,v):
        self.val=v
        if v is None: self.label.setText('—'); return
        self.label.setText(f'{v:.1f}' if isinstance(v,float) else str(v))
        if v<self.lo or v>self.hi: self.label.setStyleSheet('font-size:25px;font-weight:700;color:#ff5252;padding:8px')
        else: self.label.setStyleSheet('font-size:25px;font-weight:700;color:#65e6a4;padding:8px')

class Main(QMainWindow):
    def __init__(self):
        super().__init__(); self.setWindowTitle('MonitorTune | Buick 3800 Turbo'); self.resize(1500,900)
        self.ser=None; self.buf=bytearray(); self.data={}; self.bin=None
        root=QWidget(); self.setCentralWidget(root); outer=QVBoxLayout(root)
        bar=QHBoxLayout(); self.port=QComboBox(); self.refresh_ports()
        refresh=QPushButton('Refresh COM'); refresh.clicked.connect(self.refresh_ports)
        self.connect_btn=QPushButton('Connect ALDL'); self.connect_btn.clicked.connect(self.toggle_serial)
        self.link=QLabel('ECU DISCONNECTED'); self.link.setStyleSheet('color:#ff6868;font-weight:bold')
        bar.addWidget(QLabel('USB ALDL:')); bar.addWidget(self.port); bar.addWidget(refresh); bar.addWidget(self.connect_btn); bar.addStretch(); bar.addWidget(self.link); outer.addLayout(bar)
        self.tabs=QTabWidget(); outer.addWidget(self.tabs)
        self.tabs.addTab(self.engine_tab(),'ENGINE HMI'); self.tabs.addTab(self.dtc_tab(),'DIAGNOSTICS'); self.tabs.addTab(self.ve_tab(),'BIN / VE CALIBRATION')
        self.statusBar().showMessage('Dedicated Buick 3800 • Delco OSE $11P V104 • 8192 baud')
        self.timer=QTimer(self); self.timer.timeout.connect(self.poll); self.timer.start(25)
        self.anim=QTimer(self); self.anim.timeout.connect(lambda:self.engine.update()); self.anim.start(40)
    def refresh_ports(self):
        self.port.clear()
        for x in list_ports.comports(): self.port.addItem(f'{x.device} — {x.description}',x.device)
    def toggle_serial(self):
        if self.ser:
            self.ser.close(); self.ser=None; self.connect_btn.setText('Connect ALDL'); self.link.setText('ECU DISCONNECTED'); self.link.setStyleSheet('color:#ff6868;font-weight:bold'); return
        dev=self.port.currentData()
        if not dev: return
        try:
            self.ser=serial.Serial(dev,BAUD,bytesize=8,parity='N',stopbits=1,timeout=0)
            self.connect_btn.setText('Disconnect'); self.link.setText('ALDL LINK OPEN — WAITING FOR VALID FRAME')
            self.link.setStyleSheet('color:#f4c15d;font-weight:bold')
        except Exception as ex: QMessageBox.critical(self,'ALDL connection',str(ex))
    def engine_tab(self):
        page=QWidget(); layout=QHBoxLayout(page); self.engine=EnginePanel(); layout.addWidget(self.engine,3)
        grid=QGridLayout(); layout.addLayout(grid,2)
        specs=[('RPM','rpm',0,8000),('BOOST','psi',-15,30),('AFR','wideband',10,20),('COOLANT','°C',0,110),
          ('MAP','kPa',0,300),('IGNITION','°',-10,50),('KNOCK RETARD','°',0,8),('TPS','%',0,100),
          ('VE RAW','%',20,160),('CALC VE','%',20,160),('SPEED','km/h',0,250),('IAC','counts',0,255)]
        self.gauges={}
        for n,(title,unit,lo,hi) in enumerate(specs):
            g=Gauge(title,unit,lo,hi); self.gauges[title]=g; grid.addWidget(g,n//3,n%3)
        return page
    def dtc_tab(self):
        page=QWidget(); l=QVBoxLayout(page)
        l.addWidget(QLabel('DTC diagnostics for OSE $11P V104'))
        self.dtc_state=QLabel('DTC request/response definition has not yet been verified against the exact V104 ADX. ECU read/clear commands are intentionally locked.')
        self.dtc_state.setWordWrap(True); l.addWidget(self.dtc_state)
        row=QHBoxLayout(); self.read_dtc=QPushButton('Read DTCs (locked)'); self.clear_dtc=QPushButton('Clear DTCs (locked)')
        self.read_dtc.setEnabled(False); self.clear_dtc.setEnabled(False); row.addWidget(self.read_dtc); row.addWidget(self.clear_dtc); l.addLayout(row)
        self.dtc_table=QTableWidget(0,3); self.dtc_table.setHorizontalHeaderLabels(['Code','Description','Status']); l.addWidget(self.dtc_table)
        l.addWidget(QLabel('Unlock these actions only after validating the exact ALDL command bytes, response format and clear semantics for this OS.'))
        return page
    def ve_tab(self):
        page=QWidget(); l=QVBoxLayout(page); row=QHBoxLayout()
        self.bin_label=QLabel('No BIN loaded — expected 64 KiB'); openb=QPushButton('Open BIN'); saveb=QPushButton('Save modified BIN')
        openb.clicked.connect(self.open_bin); saveb.clicked.connect(self.save_bin)
        self.table_sel=QComboBox(); self.table_sel.addItems(['20–100 kPa VE','Boost VE']); self.table_sel.currentIndexChanged.connect(self.populate_ve)
        row.addWidget(openb); row.addWidget(saveb); row.addWidget(self.table_sel); row.addWidget(self.bin_label); l.addLayout(row)
        self.ve_table=QTableWidget(); self.ve_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeToContents); l.addWidget(self.ve_table)
        self.populate_ve(); return page
    def populate_ve(self):
        ti=self.table_sel.currentIndex(); axis=VE_AXES[ti]; self.ve_table.setRowCount(len(RPM_ROWS)); self.ve_table.setColumnCount(len(axis))
        self.ve_table.setVerticalHeaderLabels([str(x) for x in RPM_ROWS]); self.ve_table.setHorizontalHeaderLabels([str(x) for x in axis])
        for i in range(len(RPM_ROWS)):
            for j in range(len(axis)):
                v='—'
                if self.bin:
                    raw=self.bin[VE_ADDR[ti]+i*len(axis)+j]; v=f'{raw*VE_SCALE:.1f}'
                self.ve_table.setItem(i,j,QTableWidgetItem(v))
    def open_bin(self):
        p,_=QFileDialog.getOpenFileName(self,'Open 64 KiB BIN','','BIN files (*.bin);;All files (*)')
        if not p:return
        b=bytearray(Path(p).read_bytes())
        if len(b)!=BIN_SIZE:
            QMessageBox.warning(self,'Wrong BIN size',f'Expected exactly 65536 bytes; received {len(b)}.'); return
        for ti,axis in enumerate(VE_AXES):
            for i in range(len(RPM_ROWS)):
                for j in range(len(axis)):
                    v=b[VE_ADDR[ti]+i*len(axis)+j]*VE_SCALE
                    if not 20<=v<=160:
                        QMessageBox.warning(self,'BIN validation',f'VE values invalid at table {ti}, row {i}, col {j}. Wrong BIN or definition.'); return
        self.bin=b; self.bin_label.setText(Path(p).name+' • 64 KiB'); self.populate_ve()
    def save_bin(self):
        if not self.bin: QMessageBox.information(self,'BIN','Load a BIN first.'); return
        out=bytearray(self.bin); ti=self.table_sel.currentIndex(); axis=VE_AXES[ti]
        for i in range(len(RPM_ROWS)):
            for j in range(len(axis)):
                item=self.ve_table.item(i,j)
                if not item: continue
                try: v=float(item.text())
                except: continue
                if not 0<=v<=160: QMessageBox.warning(self,'Value range','VE must be 0–160%.'); return
                out[VE_ADDR[ti]+i*len(axis)+j]=max(0,min(255,round(v/VE_SCALE)))
        p,_=QFileDialog.getSaveFileName(self,'Save modified BIN','','BIN files (*.bin)')
        if p:
            Path(p).write_bytes(out); QMessageBox.information(self,'Saved','Modified BIN copy saved. No ECU flash/write is performed.')
    def poll(self):
        if not self.ser:return
        try:
            n=self.ser.in_waiting
            if n:self.buf.extend(self.ser.read(n))
            f,used=find_frame(self.buf)
            if used:self.buf=self.buf[used:]
            if f:
                self.data=decode_frame(f); self.engine.update_data(self.data)
                d=self.data
                vals={'RPM':d['rpm'],'BOOST':d['boost'],'AFR':d['afr'],'COOLANT':d['coolant'],'MAP':d['map'],
                  'IGNITION':d['spark'],'KNOCK RETARD':d['knock'],'TPS':d['tps'],'VE RAW':d['ve'],
                  'CALC VE':None,'SPEED':d['speed'],'IAC':None}
                for k,v in vals.items(): self.gauges[k].set_value(v)
                self.link.setText('ECU DATA VALID'); self.link.setStyleSheet('color:#65e6a4;font-weight:bold')
        except Exception as ex:
            self.link.setText('ALDL ERROR: '+str(ex)); self.link.setStyleSheet('color:#ff6868;font-weight:bold')
    def closeEvent(self,e):
        if self.ser:self.ser.close()
        e.accept()

if __name__=='__main__':
    app=QApplication(sys.argv); app.setStyle('Fusion'); w=Main(); w.show(); sys.exit(app.exec())
