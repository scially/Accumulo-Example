原文地址: [http://accumulo.apache.org/1.9/accumulo_user_manual.html#_writing_accumulo_clients](http://accumulo.apache.org/1.9/accumulo_user_manual.html#_writing_accumulo_clients)
## 4. Writing Accumulo Clients
### 4.1. Running Clinet Code
有3种方式来运行使用Accumulo提供的API开发的Accumulo客户端：

    1. 使用`java -jar jar`包
    2. 使用`accmulo jar`
    3. 使用bin目录下的tool.sh脚本来运行
    
为了使用Accumulo，你需要在classpath中配置你的环境变量。Accumulo依赖于Hadoop和Zookeeper，对于Hadoop，Hadoop所有的jar包都存在lib目录下面，配置文件都存在conf目录下；对于新版本的Zookeep，你只需要添加Zookee的jar包即可，而不需要将Zookeeper的lib目录下jar包全都添加。你可以使用`$ACCUMULO_HOME/bin/accumulo classpath`来查看Accumulo所依赖的jar包。

对于上述执行的第2种方法，你可以将自己写的jar包放在`/lib/exe`下，然后可以直接使用accumulo来执行。例如：假设你写了一个类`com.foo.Client`，打成了jar包，并把它放在了`lib/ext`中，那么你可以直接执行`accumulo com.foo.Client`执行你写的代码。

如果你使用Hadoop的MapReduceJob，并且使用了Accumulo，那么你可以通过`bin/tool.sh`来运行这个job。参见`map reduce example`。

### 4.2.Connecting
所有的Accumulo客户端首先要创建Accumulo instance。代码如下：
``` java
String instanceName = "myinstance";
String zooServers = "zooserver-one,zooserver-two"
Instance inst = new ZooKeeperInstance(instanceName, zooServers);

Connector conn = inst.getConnector("user", new PasswordToken("passwd"));
```
其中`PasswordToken`是`AuthenticationToken`的一个常见实现类。`AuthenticationToken`这个接口允许用户通过不同的形式（子类）来认证。The CredentialProviderToken leverages the Hadoop CredentialProviders (new in Hadoop 2.6).

For example, the CredentialProviderToken can be used in conjunction with a Java KeyStore to alleviate passwords stored in cleartext. When stored in HDFS, a single KeyStore can be used across an entire instance. Be aware that KeyStores stored on the local filesystem must be made available to all nodes in the Accumulo cluster.

```
KerberosToken token = new KerberosToken();
Connector conn = inst.getConnector(token.getPrincipal(), token);
```

The KerberosToken can be provided to use the authentication provided by Kerberos. Using Kerberos requires external setup and additional configuration, but provides a single point of authentication through HDFS, YARN and ZooKeeper and allowing for password-less authentication with Accumulo.

### 4.3.Writing Data
所有写入Accumulo的数据都通过Mutation写入，Mutation代表了一行所有族的数据变化。
Mutation可以这样创建（注意：此时只是将数据写入Mutation，并未写入Accumulo中）：
```
Text rowID = new Text("row1");
Text colFam = new Text("myColFam");
Text colQual = new Text("myColQual");
ColumnVisibility colVis = new ColumnVisibility("public");
long timestamp = System.currentTimeMillis();

Value value = new Value("myValue".getBytes());

Mutation mutation = new Mutation(rowID);
mutation.put(colFam, colQual, colVis, timestamp, value);
```

### 4.3.1.BatchWriter
BarchWriter负责将多个Mutation写入Accumulo中。BatchWriter是一种效率高的写入对象，它每次发送多个Mutations到多个TabletServers，并且自动的多次发送Mutations到相同的TabletServer以分摊网络开销。要注意的是，由于BatcherWriter是将Mutations存在内存中，所以要尽量避免任何一个到BatcherWriter中后内容发生变化。
Mutations可以这样添加到BatchWriter:
```
// BatchWriterConfig has reasonable defaults
BatchWriterConfig config = new BatchWriterConfig();
config.setMaxMemory(10000000L); // bytes available to batchwriter for buffering mutations

BatchWriter writer = conn.createBatchWriter("table", config)

writer.addMutation(mutation);

writer.close();
```
在`accumulo/docs/examples/README.batch`中有batch writer的例子。

### 4.3.2.ConditionalWriter
ConditionalWriter会对每一行开启有效，原子读写的操作。

### 4.3.3.Durability
通常，Accumulo首先会把所有写入操作全部更新到WAL中。每次改变都会反映到HDFS文件系统，并会同步到硬盘中以持久化。在某次失败事件中，在内存中的数据会被WAL中的数据替换。在HDFS中的文件都是以块存储，并且是有多个副本。发送更新到副本，并等待同步到磁盘可以显提高写入速度。

Accumulo允许用户在写入数据是使用不同的形式来持久化数据：
* none: 不使用WAL，没有持久化数据
* log: WAL被使用，但是不会刷新。服务器发生异常可能会导致最新的数据丢失
* flush: 更新被写入到WAL，并刷新到副本，服务器异常不太可能导致数据丢失
* sync:更新被写入WAL，并更新所有副本到磁盘。即使整个集群挂掉，数据也不会丢失。

用户可以设置默认持久特性在shell中。当写入数据时，用户可以配置BatchWriter或者ConditionWriter使用不同级别的持久特性，这会替换默认设置：

```
BatchWriterConfig cfg = new BatchWriterConfig();
// We don't care about data loss with these writes:
// This is DANGEROUS:
cfg.setDurability(Durability.NONE);

Connection conn = ... ;
BatchWriter bw = conn.createBatchWriter(table, cfg);
```

## 4.4.Reading Data
Accumulo在根据key取数据时效率很高，也可以根据连续的key范围返回这个区间的值。

### 4.4.1.Scanner

使用Scanner取数据，它就像一个迭代器。Scanners也可以根据开始键值和终止键值返回一系列的列。
```
// specify which visibilities we are allowed to see
Authorizations auths = new Authorizations("public");

Scanner scan =
    conn.createScanner("table", auths);

scan.setRange(new Range("harry","john"));
scan.fetchColumnFamily(new Text("attributes"));

for(Entry<Key,Value> entry : scan) {
    Text row = entry.getKey().getRow();
    Value value = entry.getValue();
}
```
### 4.4.2.Isolated Scanner

### 4.4.3.BatchScanner
对于某些类型的读取，同时检索多个范围数据更有效。例如，当访问一组不连续的行时，会出现这种情况，这些行的ID已从二级索引中检索到。
BatchScanner配置与Scanner相似。它需要传入多个范围，而不是一个范围。注意，BatchScanner返回的key不是有序的，因为流传输的key是从多个TabletServers得到的。
```
ArrayList<Range> ranges = new ArrayList<Range>();
// populate list of ranges ...

BatchScanner bscan =
    conn.createBatchScanner("table", auths, 10);
bscan.setRanges(ranges);
bscan.fetchColumnFamily("attributes");

for(Entry<Key,Value> entry : bscan) {
    System.out.println(entry.getValue());
}
```
参见`accumulo\docs\examples\README.batch`

4.5. Proxy
代理API可以让用户使用其他语言来与Accumulo交互。Accumulo中提供了代理服务器，客户端可以进一步被生成。代理API可以来代替传统的ZookeeperInstance来提供TCP端口（需要防火墙开启）来与Accumulo通信，而不需要与集群的TabletServer直接通信。

### 4.5.1.Prerequisites

代理服务器可以放在任何一个节点（首先要保证可以与Accumulo通信），然后他可以与Master、Zookeeper、NameNode和DataNodes通信。代理客户端只需要和代理服务器通信即可。

### 4.5.2.Configuration
代理服务器的配置文件如下，你也至少需要提供如下配置：
```
protocolFactory=org.apache.thrift.protocol.TCompactProtocol$Factory
tokenClass=org.apache.accumulo.core.client.security.tokens.PasswordToken
port=42424
instance=test
zookeepers=localhost:2181
```

你可以在`$ACCUMULO_HOME/proxy/proxy.properties`找到配置文件示例。

此示例配置文件进一步演示了如何通过MockAccumulo或MiniAccumuloCluster连接代理服务器。

### 4.5.3.Running the Proxy Server
在创建好配置文件后，可以使用如下命令来启用代理（假设你的配置文件名为:`config.properties`）
```
$ACCUMULO_HOME/bin/accumulo proxy -p config.properties
```

### 4.5.4.Creating a Proxy Client

## 5.Development Clients

通常，Accumulo包含了大量很多依赖环境。即使是一个单机环境，Accumulo也需要Hadoop、Zookeeper，Accumulo Master，一个Tablet Server等。如果你想使用Accumulo来写一个单元测试，你需要首先配置大量环境。

### 5.1.Mock Accumulo
MockAccumulo支持大量的伪ClientAPI，无需登录、权限等。同时也支持迭代器和结合器。注意：MockAccumulo将所有数据存放在内存中，因此在运行期间不会保留任何数据和设置。

正常连接方式是：
```
Instance instance = new ZooKeeperInstance(...);
Connector conn = instance.getConnector(user, passwordToken);
```
为了与MockAccumulo交互，使用MockInstance来代理ZookeeperInstance:
```
Instance instance = new MockInstance();
```
在shell中，你也可以用`--fake`选项启用MockAccumulo：
```
$ ./bin/accumulo shell --fake -u root -p ''

Shell - Apache Accumulo Interactive Shell
-
- version: 1.6
- instance name: fake
- instance id: mock-instance-id
-
- type 'help' for a list of available commands
-

root@fake> createtable test

root@fake test> insert row1 cf cq value
root@fake test> insert row2 cf cq value2
root@fake test> insert row3 cf cq value3

root@fake test> scan
row1 cf:cq []    value
row2 cf:cq []    value2
row3 cf:cq []    value3

root@fake test> scan -b row2 -e row2
row2 cf:cq []    value2

root@fake test>
```

当使用MapReduceJob时，你也可以设置MockAccmumlo的输入和输出：
```
AccumuloInputFormat.setMockInstance(job, "mockInstance");
AccumuloOutputFormat.setMockInstance(job, "mockInstance");
```
## 5.2.Mini Accumulo Cluster
为了单元测试，Mock Accumulo已经提供了轻量级的Accumulo Client API实现，但是大多数情况还是希望模拟一个更加真实的集群环境下的Accumulo环境，此时MiniAccumulo  Cluster就派上了用场，通过一些配置（包括Zookeeper等）就可以在本地系统启动集群。
为了使用MiniAccumulo Cluster，你首先要创建一个空的目录和传入一个密码做为参数：
```java
File tempDirectory = // JUnit and Guava supply mechanisms for creating temp directories
MiniAccumuloCluster accumulo = new MiniAccumuloCluster(tempDirectory, "password");
accumulo.start();
```
一旦上述代码执行完毕，我们就可以使用模拟的集群环境
```
Instance instance = new ZooKeeperInstance(accumulo.getInstanceName(), accumulo.getZooKeepers());
Connector conn = instance.getConnector("root", new PasswordToken("password"));
```

停止模拟集群下的Accumulo
```
accumulo.stop();
```


