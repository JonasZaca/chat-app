package com.moblab.tethering;


import java.util.ArrayList;


public interface FinishScanListener {

    public void onFinishScan(ArrayList<ClientScanResult> clients);

}