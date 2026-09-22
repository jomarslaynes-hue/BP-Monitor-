package com.example.bloodpressurelogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName="bp_readings")
data class BPReading(@PrimaryKey(autoGenerate=true) val id:Long=0,val systolic:Int,val diastolic:Int,val pulse:Int,val note:String,val timestamp:Long=System.currentTimeMillis())

@Dao interface BPDao { @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC") fun all():Flow<List<BPReading>>; @Insert suspend fun insert(r:BPReading); @Delete suspend fun delete(r:BPReading) }

@Database(entities=[BPReading::class],version=1,exportSchema=false)
abstract class BPDatabase:RoomDatabase(){ abstract fun dao():BPDao; companion object { @Volatile private var i:BPDatabase?=null; fun get(c:android.content.Context)=i?:synchronized(this){i?:Room.databaseBuilder(c.applicationContext,BPDatabase::class.java,"blood_pressure.db").build().also{i=it}} } }

class BPVM(private val dao:BPDao):ViewModel(){ val readings=dao.all().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList()); fun add(s:Int,d:Int,p:Int,n:String)=viewModelScope.launch{dao.insert(BPReading(systolic=s,diastolic=d,pulse=p,note=n))}; fun delete(r:BPReading)=viewModelScope.launch{dao.delete(r)} }

@Composable fun Trend(readings:List<BPReading>){val d=readings.sortedBy{it.timestamp}.takeLast(30);Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("BP trend",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));if(d.size<2)Text("Add at least two readings to see the chart.") else {Canvas(Modifier.fillMaxWidth().height(210.dp)){val min=40f;val max=200f;fun y(v:Int)=size.height-((v-min)/(max-min))*size.height;fun x(i:Int)=i.toFloat()/(d.size-1)*size.width;fun series(v:List<Int>){val p=Path();v.forEachIndexed{idx,n->val q=Offset(x(idx),y(n.coerceIn(40,200)));if(idx==0)p.moveTo(q.x,q.y)else p.lineTo(q.x,q.y)};drawPath(p,MaterialTheme.colorScheme.primary,Stroke(4f))};series(d.map{it.systolic});series(d.map{it.diastolic})};Text("Latest 30 readings")}}}}

@Composable fun App(vm:BPVM){var s by remember{mutableStateOf("")};var d by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var n by remember{mutableStateOf("")};var tab by remember{mutableIntStateOf(0)};val rs by vm.readings.collectAsState();MaterialTheme{Scaffold(topBar={TopAppBar(title={Text("BP Logger")})}){pad->Column(Modifier.padding(pad)){TabRow(tab){Tab(tab==0,{tab=0},{Text("Dashboard")});Tab(tab==1,{tab=1},{Text("History")})};if(tab==0)LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Record blood pressure",style=MaterialTheme.typography.headlineSmall);OutlinedTextField(s,{s=it.filter(Char::isDigit)},label={Text("Systolic (mmHg)")},Modifier.fillMaxWidth());OutlinedTextField(d,{d=it.filter(Char::isDigit)},label={Text("Diastolic (mmHg)")},Modifier.fillMaxWidth());OutlinedTextField(p,{p=it.filter(Char::isDigit)},label={Text("Pulse (bpm)")},Modifier.fillMaxWidth());OutlinedTextField(n,{n=it},label={Text("Note")},Modifier.fillMaxWidth());Button({val a=s.toIntOrNull();val b=d.toIntOrNull();val c=p.toIntOrNull();if(a!=null&&b!=null&&c!=null){vm.add(a,b,c,n);s="";d="";p="";n=""}},Modifier.fillMaxWidth()){Text("Save reading")}};item{Trend(rs)};item{if(rs.isNotEmpty())Text("Latest: ${rs.first().systolic}/${rs.first().diastolic} mmHg • ${rs.first().pulse} bpm")}}else LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Reading history",style=MaterialTheme.typography.headlineSmall)};items(rs,key={it.id}){r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("${r.systolic}/${r.diastolic} mmHg",style=MaterialTheme.typography.titleLarge);Text("Pulse: ${r.pulse} bpm");Text(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date(r.timestamp)));if(r.note.isNotBlank())Text("Note: ${r.note}");TextButton({vm.delete(r)}){Text("Delete")}}}}}}}}}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);val dao=BPDatabase.get(this).dao();setContent{App(viewModel(factory=object:androidx.lifecycle.ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=BPVM(dao) as T}))}}}
