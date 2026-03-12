module com.barnwatch9000
{
    requires java.desktop;
    requires java.net.http;
    requires java.sql;

    requires javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.swing;

    requires org.xerial.sqlitejdbc;
    requires uk.co.caprica.vlcj;

    exports com.barnwatch9000;
}
