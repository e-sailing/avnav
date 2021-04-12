package de.wellenvogel.avnav.worker;

import org.json.JSONException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import de.wellenvogel.avnav.mdns.Resolver;
import de.wellenvogel.avnav.util.AvnUtil;
import de.wellenvogel.avnav.util.NmeaQueue;

public abstract class ChannelWorker extends Worker{
    protected GpsService gpsService;
    protected NmeaQueue queue;
    ChannelWorker(String name, GpsService ctx, NmeaQueue queue){
        super(name);
        this.gpsService =ctx;
        this.queue=queue;
    }

    protected ConnectionReaderWriter.ConnectionProperties getConnectionProperties() throws JSONException {
            ConnectionReaderWriter.ConnectionProperties rt=new ConnectionReaderWriter.ConnectionProperties();
            rt.readData=true;
            if (parameterDescriptions.has(SEND_DATA_PARAMETER))rt.writeData=Worker.SEND_DATA_PARAMETER.fromJson(parameters);
            if (parameterDescriptions.has(FILTER_PARAM)) rt.readFilter= AvnUtil.splitNmeaFilter(Worker.FILTER_PARAM.fromJson(parameters));
            if (parameterDescriptions.has(SEND_FILTER_PARAM)) rt.writeFilter=AvnUtil.splitNmeaFilter(Worker.SEND_FILTER_PARAM.fromJson(parameters));
            rt.sourceName=getSourceName();
            if (parameterDescriptions.has(READ_TIMEOUT_PARAMETER)) rt.noDataTime=Worker.READ_TIMEOUT_PARAMETER.fromJson(parameters);
            if (parameterDescriptions.has(READTIMEOUT_CLOSE_PARAMETER)) rt.closeOnReadTimeout=READTIMEOUT_CLOSE_PARAMETER.fromJson(parameters);
            if (parameterDescriptions.has(CONNECT_TIMEOUT_PARAMETER)) rt.connectTimeout =Worker.CONNECT_TIMEOUT_PARAMETER.fromJson(parameters);
            if (parameterDescriptions.has(WRITE_TIMEOUT_PARAMETER)) rt.writeTimeout=Worker.WRITE_TIMEOUT_PARAMETER.fromJson(parameters);
            if (parameterDescriptions.has(BLACKLIST_PARAMETER)) rt.blacklist=AvnUtil.splitNmeaFilter(BLACKLIST_PARAMETER.fromJson(parameters));
            return rt;
    }

    protected InetSocketAddress resolveAddress(String target, int port,int startSequence,boolean forceNew){
        //we must wait more then the resolver to avoid filling it up with our requests
        long MAXWAIT= Resolver.RETRIGGER_TIME*(Resolver.MAX_RETRIGGER+1);
        InetSocketAddress address = null;
        try{
            InetAddress ip= Inet4Address.getByName(target);
            address=new InetSocketAddress(target, port);
        }catch(UnknownHostException e){
            if (target.endsWith("local")) {
                setStatus(WorkerStatus.Status.STARTED, "waiting for MDNS resolve for " + target);
                final AddressHolder resolved= new AddressHolder(null);
                gpsService.resolveMdnsHost(target, new Resolver.ResolveHostCallback() {
                    @Override
                    public void resolve(String name, InetAddress host) {
                        if (name.equals(target)) {
                            resolved.setAddress(host);
                            synchronized (waiter) {
                                waiter.notifyAll();
                            }
                        }
                    }
                }, forceNew);
                long start = System.currentTimeMillis();
                InetAddress ip = null;
                while ((start + MAXWAIT) > System.currentTimeMillis() && ! shouldStop(startSequence)) {
                    if (resolved.isResolved()) {
                        ip = resolved.getAddress();
                        break;
                    }
                    sleep(1000);
                }
                if (ip == null) {
                    return null;
                }
                address=new InetSocketAddress(ip,port);
            }
            else{
                setStatus(WorkerStatus.Status.ERROR,"unable to resolve "+target);
                if (! shouldStop(startSequence)) sleep(5000);
                return null;
            }
        }
        return address;
    }

    static class AddressHolder{
        InetAddress address;
        boolean isResolved;
        AddressHolder(InetAddress address){
            this.address=address;
        }
        synchronized InetAddress getAddress(){
            return address;
        }
        synchronized void setAddress(InetAddress address){
            this.address=address;
            this.isResolved=true;
        }
        synchronized boolean isResolved(){
            return isResolved;
        }
    }
}
