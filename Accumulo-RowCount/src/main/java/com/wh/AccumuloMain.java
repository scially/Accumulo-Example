package com.wh;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.wh.utils.AccumuloMR;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;

public class AccumuloMain {
    private static final Logger LOGGER = Logger.getLogger(AccumuloMain.class);

    public static void main(String[] args) throws Exception {
        AccumuloOpts opts = new AccumuloOpts();
        JCommander command = JCommander.newBuilder()
                .addObject(opts)
                .build();
        command.setProgramName("hadoop jar");
        try {
            command.parse(args);
        }catch (ParameterException ex){
            ex.usage();
            System.out.println(ex.getMessage());
            return;
        }
        run(opts);
    }

    private static void run(AccumuloOpts opts)
            throws ClassNotFoundException, AccumuloSecurityException, InterruptedException, IOException {
        if(StringUtils.isNoneEmpty(opts.getHadoopHome())){
            System.setProperty("hadoop.home.dir",opts.getHadoopHome()) ;
        } else{
            opts.setHadoopHome(System.getenv("HADOOP_HOME"));
            if(StringUtils.isEmpty(opts.getHadoopHome())){
                LOGGER.error("the HADOOP_HOME env is not set!");
                return;
            }
        }

        AccumuloMR.getRowCount(opts.getInstance(),
                opts.getZookeepers(),
                opts.getUser(),
                opts.getPwd(),
                opts.getTableName(),
                opts.getOutput());
    }
}
