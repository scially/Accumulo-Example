package com.wh.utils;

import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.ClientConfiguration;
import org.apache.accumulo.core.client.mapreduce.AccumuloInputFormat;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;

import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;

public class AccumuloMR {
    private static final int KEY = 1;
    private static final long VAL=1L;
    private static class AMapper extends Mapper<Key, Value, IntWritable, LongWritable> {
        private final static IntWritable id = new IntWritable(1);
        @Override
        protected void map(Key key, Value value, Context context)
                throws IOException, InterruptedException {
            context.write(new IntWritable(KEY), new LongWritable(VAL));
        }
    }
    private static class AReducer extends Reducer<IntWritable, LongWritable, IntWritable, LongWritable> {
        @Override
        protected void reduce(IntWritable key, Iterable<LongWritable> values, Context context)
                throws IOException, InterruptedException {
            Long rowCount = 0L;
            for(LongWritable count : values ){
                rowCount += count.get();
            }
            context.write(key, new LongWritable(rowCount));
        }
    }
    public static void getRowCount(String instance, String zookeepers,
                                   String user, String pwd,
                                   String tableName, String output)
            throws IOException, AccumuloSecurityException, ClassNotFoundException, InterruptedException {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf,"AccumuloRowCount");
        job.setJarByClass(AccumuloMR.class);
        job.setMapperClass(AccumuloMR.AMapper.class);
        job.setCombinerClass(AccumuloMR.AReducer.class);
        job.setReducerClass(AccumuloMR.AReducer.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(LongWritable.class);

        AccumuloInputFormat.setConnectorInfo(job, user, new PasswordToken(pwd));
        //AccumuloInputFormat.setScanAuthorizations(job, );
        ClientConfiguration acf = ClientConfiguration
                .loadDefault()
                .withInstance(instance)
                .withZkHosts(zookeepers);
        AccumuloInputFormat.setZooKeeperInstance(job, acf);
        AccumuloInputFormat.setInputTableName(job, tableName);
        TextOutputFormat.setOutputPath(job, new Path(output));
        job.setInputFormatClass(AccumuloInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.waitForCompletion(true);

    }
}
