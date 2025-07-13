package com.zhangwy.scannerapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;



public class MainActivity extends AppCompatActivity {
    private Button conn_btn,scan_btn,preview_btn;
    private EditText ip_field,name_field,pwd_field;
    private TextView stat_holder;
    private Spinner resolution_sp,mode_sp,file_type_sp;
    public ChannelShell ssh_client;
    public ChannelSftp sftp_client;
    public Boolean isConnected = false,isScanned = false;
    public Session session;
    public String[] Resolutions = {"","75dpi","150dpi","300dpi","600dpi"};
    public String[] Modes = {"","Color","Gray","Lineart"};
    public String file_type;
    public String pwd = "Zwy20030304";

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        resolution_sp = findViewById(R.id.resolution_spinner);
        mode_sp = findViewById(R.id.mode_spinner);
        file_type_sp = findViewById(R.id.file_type_spinner);
        conn_btn = findViewById(R.id.conn_btn);
        scan_btn = findViewById(R.id.scan_btn);
        preview_btn = findViewById(R.id.preview_btn);
        ip_field = findViewById(R.id.ip_edit);
        name_field = findViewById(R.id.name_edit);
        pwd_field = findViewById(R.id.pwd_edit);
        stat_holder = findViewById(R.id.stat_holder);
        conn_btn.setOnClickListener(v ->{
            String ip = ip_field.getText().toString();
            String name = name_field.getText().toString();
            process_conn_btn(ip,name);
        });
        scan_btn.setOnClickListener(v ->{
            int resolution = resolution_sp.getSelectedItemPosition();
            int mode = mode_sp.getSelectedItemPosition();
            process_scan_btn(resolution,mode);
        });
        preview_btn.setOnClickListener(v ->{
            process_preview_btn(v.getContext());
        });
        requestPermissions(
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                },
                1
        );
    }
    private void process_conn_btn(String ip,String name) {
        String finalPwd = pwd;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(name, ip, 22);
                session.setPassword(finalPwd);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setTimeout(30000);
                session.connect();
                runOnUiThread(() -> stat_holder.setText("连接成功"));
                isConnected = true;
            } catch (JSchException e) {
                runOnUiThread(() -> stat_holder.setText("SSH错误: " + e.getMessage()));
                isConnected = false;
            }
        }).start();
    }

    private void process_scan_btn(int resolution,int mode){
        if(!isConnected){
            stat_holder.setText("请先连接扫描仪");
            return;
        }
        file_type = file_type_sp.getSelectedItem().toString();
        if(file_type.contains("pdf")){
            file_type = "pdf";
        }
        String command = "scanimage " + "--format=" + file_type + " --output-file " + "/home/zwy/pngs/" + "t." + file_type;
        if(mode != 0){
            command = command + " --mode " + Modes[mode];
        }
        if(resolution != 0){
            command = command + " --resolution " + Resolutions[resolution];
        }
        command = command + " --progress";
        run_command(command, true, success -> {
            isScanned = success;
            runOnUiThread(() -> {
                stat_holder.setText(success ? "扫描完成，可点击'保存文件'保存" : "扫描失败");
            });
        });
    }


private void process_preview_btn(Context context) {
    if (!isScanned) {
        stat_holder.setText("请先扫描");
        return;
    }

    stat_holder.setText("正在下载文件...");
    getFile(context, new FileCallback() {
        @Override
        public void onSuccess(File file) {
            stat_holder.setText("文件获取成功");
            // 这里可以显示图片
            displayImage(file);
        }

        @Override
        public void onFailure(Exception e) {
            stat_holder.setText("文件获取错误: " + e.getMessage());
        }
    });
}

    private void run_command(String command, Boolean is_sudo, Consumer<Boolean> callback){
        final boolean[] success = {true};
        new Thread(() ->{
            try{
                ssh_client = (ChannelShell) session.openChannel("shell");
                InputStream in = ssh_client.getInputStream();
                OutputStream out = ssh_client.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                ssh_client.connect();
                if(is_sudo){
                    String fullCommand = "echo '" + pwd + "' | sudo -S " + command;
                    out.write((fullCommand + "\n").getBytes());
                    out.flush();
                }else{
                    out.write((command + "\n").getBytes());
                    out.flush();
//                    ssh_client.setCommand(command);
                }
                runOnUiThread(() -> {
                    stat_holder.setText("已下发指令 请等待");
                });
                String line;
                while ((line = reader.readLine()) != null) {
                    final String lastLine = line; // 不断更新为最后一行
                    if(lastLine.contains("Progress")){
                        String result = lastLine.replaceFirst("Progress", "正在扫描:");
                        result = result + "请耐心等待";
                        String finalResult = result;
                        runOnUiThread(() -> {
                            stat_holder.setText(finalResult);
                        });
                    }
                    if(lastLine.contains("100.0%")){
                        success[0] = true;
                        break;
                    }
                }
            }catch (Exception e){
                runOnUiThread(() -> stat_holder.setText("SSH错误: " + e.getMessage()));
                success[0] = false;
            }finally {
                if (ssh_client != null) {
                    ssh_client.disconnect();
                }
                callback.accept(success[0]);
            }
        }).start();
    }

    private void getFile(Context context, FileCallback callback) {
        new Thread(() -> {
            try {
                // 1. 检查SFTP连接
                if (session == null || !session.isConnected()) {
                    throw new JSchException("SSH会话未连接");
                }

                // 2. 创建SFTP通道
                sftp_client = (ChannelSftp) session.openChannel("sftp");
                sftp_client.connect();

                // 3. 创建本地临时文件
                File outputFile = new File(context.getCacheDir(), "t." + file_type);

                // 4. 下载文件
                sftp_client.get("/home/zwy/pngs/t." + file_type, new FileOutputStream(outputFile));
                sftp_client.disconnect();

                // 5. 成功回调
                runOnUiThread(() -> callback.onSuccess(outputFile));

            } catch (Exception e) {
                // 6. 失败回调
                runOnUiThread(() -> {
                    stat_holder.setText("下载失败: " + e.getMessage());
                    callback.onFailure(e);
                });
            }
        }).start();
    }

    private void displayImage(File file){
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, ShowImage.class);
//        Toast.makeText(this, "图片地址:" + file.getAbsoluteFile(), Toast.LENGTH_SHORT).show();
        intent.putExtra("imagepath",file.getAbsolutePath());
        intent.putExtra("file_type",file_type);
        startActivity(intent);
    }

    interface FileCallback {
        void onSuccess(File file);
        void onFailure(Exception e);
    }
}