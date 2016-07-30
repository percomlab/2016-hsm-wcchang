package util;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.crypto.Data;

public class TimeFormat
{
    private SimpleDateFormat simpleDateFormat;
    public TimeFormat() {
		simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
	}
    
    public Date getCurrentTime() throws ParseException {
		Date currentTime = new Date();
		return simpleDateFormat.parse(simpleDateFormat.format(currentTime));
	}
    public String getDateString(Date date)
    {
        return simpleDateFormat.format(date);
    }
    public Date toDate(String string){  
		Date date = null;
		try {
		    date = simpleDateFormat.parse(string);
		} catch (ParseException e) {
		    e.printStackTrace();
		}    
		return date;
    }
    public long getPassingTime(Date date){  
    	Date currentTime = null;
		Date lastTime = null;
		try {
		    currentTime = simpleDateFormat.parse(simpleDateFormat.format(new Date()));
		    lastTime = simpleDateFormat.parse(simpleDateFormat.format(date));
		} catch (ParseException e) {
		    e.printStackTrace();
		}    
		long diff = currentTime.getTime() - lastTime.getTime();
    	return diff;
    }
    public long getDiff(Date newTime, Date oldTime){
    	Date currentTime = null;
		Date lastTime = null;
		try {
		    currentTime = simpleDateFormat.parse(simpleDateFormat.format(newTime));
		    lastTime = simpleDateFormat.parse(simpleDateFormat.format(oldTime));
		} catch (ParseException e) {
		    e.printStackTrace();
		}    
		long diff = currentTime.getTime() - lastTime.getTime();
    	return diff;
    }
//    public String getRoundedString(double input, int numberOfDigits)
//    {
//        NumberFormat format = NumberFormat.getInstance();
//        format.setRoundingMode(RoundingMode.HALF_UP);
//        format.setMaximumFractionDigits(numberOfDigits);
//        return format.format(input);
//    }
}
