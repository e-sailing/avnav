/**
 * Created by andreas on 04.05.14.
 */
avnav.provide('avnav.nav.GpsData');



/**
 * the handler for the gps data
 * query the server...
 * @param {avnav.util.PropertyHandler} propertyHandler
 * @param {avnav.nav.NavObject} navobject
 * @constructor
 */
avnav.nav.GpsData=function(propertyHandler,navobject){
    /** @private */
    this.propertyHandler=propertyHandler;
    /** @private */
    this.navobject=navobject;
    /** @private */
    this.gpsdata=new avnav.nav.navdata.GpsInfo();
    /** @private */
    this.formattedData= {
        gpsPosition:"NO FIX",
        gpsCourse:"0",
        gpsSpeed:"0",
        gpsTime:"---",
        nmeaStatusColor:"red",
        nmeaStatusText:"???",
        aisStatusColor: "red",
        aisStatusText: "???"
    };
    /** {avnav.util.Formatter} @private */
    this.formatter=new avnav.util.Formatter();
    this.timer=null;
    /** {Boolean} @private */
    this.validPosition=false;
    this.gpsErrors=0;
    this.NM=this.propertyHandler.getProperties().NM;
    this.startQuery();
    for (var k in this.formattedData){
        this.navobject.registerValueProvider(k,this,this.getFormattedGpsValue);
    }
};

/**
 *
 * @param data
 * @private
 */
avnav.nav.GpsData.prototype.handleGpsResponse=function(data){
    var gpsdata=new avnav.nav.navdata.GpsInfo();
    gpsdata.rtime=null;
    if (data.time != null) gpsdata.rtime=new Date(data.time);
    gpsdata.lon=data.lon;
    gpsdata.lat=data.lat;
    gpsdata.course=data.course;
    if (gpsdata.course === undefined) gpsdata.course=data.track;
    gpsdata.speed=data.speed*3600/this.NM;
    gpsdata.valid=true;
    gpsdata.raw=data.raw;
    this.gpsdata=gpsdata;
    var formattedData={};
    formattedData.gpsPosition=this.formatter.formatLonLats(gpsdata);
    formattedData.gpsCourse=this.formatter.formatDecimal(gpsdata.course||0,3,0);
    formattedData.gpsSpeed=this.formatter.formatDecimal(gpsdata.speed||0,2,1);
    formattedData.gpsTime=this.formatter.formatTime(gpsdata.rtime||new Date());
    formattedData.nmeaStatusColor="red";
    formattedData.nmeaStatusText="???"
    try {
        if (data.raw && data.raw.status && data.raw.status.nmea){
            formattedData.nmeaStatusColor = data.raw.status.nmea.status;
            formattedData.nmeaStatusText=data.raw.status.nmea.source+":"+data.raw.status.nmea.info;
        }
    }catch(e){}
    formattedData.aisStatusColor="red";
    formattedData.aisStatusText="???"
    try {
        if (data.raw && data.raw.status && data.raw.status.ais){
            formattedData.aisStatusColor = data.raw.status.ais.status;
            formattedData.aisStatusText=data.raw.status.ais.source+":"+data.raw.status.ais.info;
        }
    }catch(e){}

    this.formattedData=formattedData;
};

/**
 * @private
 */
avnav.nav.GpsData.prototype.startQuery=function(){
    var url=this.propertyHandler.getProperties().navUrl;
    var timeout=this.propertyHandler.getProperties().positionQueryTimeout;
    var self=this;
    $.ajax({
        url: url,
        dataType: 'json',
        cache:	false,
        success: function(data,status){
            if (data['class'] != null && data['class'] == "TPV" &&
                data.tag != null && data.lon != null && data.lat != null &&
                data['mode'] != null && data['mode'] >=1){
                self.handleGpsResponse(data);
                log("gpsdata: "+self.formattedData.gpsPosition);
                self.handleGpsStatus(true);
            }
            else{
                self.handleGpsStatus(false);
            }
            self.timer=window.setTimeout(function(){
                self.startQuery();
            },timeout);
        },
        error: function(status,data,error){
            log("query position error");
            self.handleGpsStatus(false);
            self.timer=window.setTimeout(function(){
                self.startQuery();
            },timeout);
        },
        timeout: 10000
    });

};

/**
 * handle the status and trigger the FPS event
 * @param success
 */
avnav.nav.GpsData.prototype.handleGpsStatus=function(success){
    if (! success){
        this.gpsErrors++;
        if (this.gpsErrors > this.propertyHandler.getProperties().maxGpsErrors){
            log("lost gps");
            this.validPosition=false;
            this.gpsdata.valid=false;
            //continue to count errrors...
        }
        else{
            return;
        }
    }
    else {
        this.gpsErrors=0;
        this.validPosition=true;
    }
    this.navobject.gpsEvent();
};

/**
 * return the current gpsdata
 * @returns {avnav.nav.navdata.GpsInfo}
 */
avnav.nav.GpsData.prototype.getGpsData=function(){
    return this.gpsdata;
};

/**
 * get the formatted value of a GPS item
 * currently the status is not considered
 * @param name
 * @returns {*}
 */
avnav.nav.GpsData.prototype.getFormattedGpsValue=function(name){
    return this.formattedData[name];
};

/**
 * get the currently defined names for formatted data
 * @returns {Array}
 */
avnav.nav.GpsData.prototype.getValueNames=function(){
    var rt=new Array();
    for (var k in this.formattedData){
        rt.push(k);
    }
    return rt;
};


