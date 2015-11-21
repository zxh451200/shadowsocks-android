/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2015 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package com.github.shadowsocks.utils

import java.io.{File, IOException}
import java.util.concurrent.Executors

import android.net.{LocalServerSocket, LocalSocket, LocalSocketAddress}
import android.util.Log
import com.github.shadowsocks.BaseService

class TrafficMonitorThread(service: BaseService) extends Thread {

  val TAG = "TrafficMonitorThread"
  val PATH = "/data/data/com.github.shadowsocks/stat_path"

  @volatile var serverSocket: LocalServerSocket = null
  @volatile var isRunning: Boolean = true

  def closeServerSocket() {
    if (serverSocket != null) {
      try {
        serverSocket.close()
      } catch {
        case _: Exception => // ignore
      }
      serverSocket = null
      }
  }

  def stopThread() {
    isRunning = false
    closeServerSocket()
  }

  override def run() {

    try {
      new File(PATH).delete()
    } catch {
      case _: Exception => // ignore
    }

    try {
      val localSocket = new LocalSocket
      localSocket.bind(new LocalSocketAddress(PATH, LocalSocketAddress.Namespace.FILESYSTEM))
      serverSocket = new LocalServerSocket(localSocket.getFileDescriptor)
    } catch {
      case e: IOException =>
        Log.e(TAG, "unable to bind", e)
        return
    }

    val pool = Executors.newFixedThreadPool(1)

    while (isRunning) {
      try {
        val socket = serverSocket.accept()

        pool.execute(() => {
          try {
            val input = socket.getInputStream
            val output = socket.getOutputStream

            val buffer = new Array[Byte](256)
            val size = input.read(buffer)

            val array = new Array[Byte](size)
            Array.copy(buffer, 0, array, 0, size)

            val stat = new String(array, "UTF-8").split("\\|")
            if (stat.length == 2) {
              TrafficMonitor.update(stat(0).toLong, stat(1).toLong)
              service.updateTrafficTotal(TrafficMonitor.txLast, TrafficMonitor.rxLast)
              service.updateTrafficRate(TrafficMonitor.getTxRate, TrafficMonitor.getRxRate,
                TrafficMonitor.getTxTotal, TrafficMonitor.getRxTotal)
            }

            output.write(0)

            input.close()
            output.close()

          } catch {
            case e: Exception =>
              Log.e(TAG, "Error when recv traffic stat", e)
          }

          // close socket
          try {
            socket.close()
          } catch {
            case _: Exception => // ignore
          }

        })
      } catch {
        case e: IOException =>
          Log.e(TAG, "Error when accept socket", e)
          return
      }
    }
  }
}
