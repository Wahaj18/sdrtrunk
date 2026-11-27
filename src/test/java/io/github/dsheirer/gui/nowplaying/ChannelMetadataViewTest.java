/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.gui.nowplaying;

import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.channel.metadata.ChannelAndMetadata;
import io.github.dsheirer.channel.metadata.ChannelMetadata;
import io.github.dsheirer.channel.metadata.ChannelMetadataModel;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.controller.channel.ChannelProcessingManager;
import io.github.dsheirer.controller.channel.map.ChannelMapModel;
import io.github.dsheirer.settings.Settings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the now playing channels view.
 */
public class ChannelMetadataViewTest
{
    @BeforeAll
    public static void startToolkit() throws Exception
    {
        //Run without a display
        System.setProperty("glass.platform", "Headless");
        System.setProperty("prism.order", "sw");
        CompletableFuture<Void> started = new CompletableFuture<>();

        try
        {
            Platform.startup(() -> started.complete(null));
        }
        catch(IllegalStateException e)
        {
            started.complete(null); //Already started
        }

        started.get(10, TimeUnit.SECONDS);
    }

    /**
     * Stopping all channels (e.g. switching playlists) removes the metadata of every channel from the model before
     * the view removes the rows.  Removing the selected row moves the selection to a row whose channel is gone.
     */
    @Test
    public void selectionMovesToRowOfStoppedChannel() throws Exception
    {
        Settings settings = new Settings();
        AliasModel aliasModel = new AliasModel();
        ChannelProcessingManager channelProcessingManager = new ChannelProcessingManager(new ChannelMapModel(),
            null, null, aliasModel, settings);
        ChannelMetadataModel model = channelProcessingManager.getChannelMetadataModel();
        ChannelMetadata first = new ChannelMetadata(aliasModel);
        ChannelMetadata second = new ChannelMetadata(aliasModel);
        CompletableFuture<ChannelMetadataView> created = new CompletableFuture<>();
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        Thread.UncaughtExceptionHandler[] handler = new Thread.UncaughtExceptionHandler[1];

        Platform.runLater(() -> {
            handler[0] = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> error.complete(throwable));
            ChannelMetadataView view = new ChannelMetadataView(channelProcessingManager, null, settings, null);
            model.add(new ChannelAndMetadata(new Channel("first"), List.of(first)));
            model.add(new ChannelAndMetadata(new Channel("second"), List.of(second)));
            created.complete(view);
        });

        ChannelMetadataView view = created.get(10, TimeUnit.SECONDS);

        CompletableFuture<Void> selected = new CompletableFuture<>();
        Platform.runLater(() -> {
            TableView<?> table = (TableView<?>)view.lookup(".table-view");
            table.getSelectionModel().select(0);
            selected.complete(null);
        });
        selected.get(10, TimeUnit.SECONDS);

        model.remove(first);
        model.remove(second);

        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            Thread.currentThread().setUncaughtExceptionHandler(handler[0]);
            view.dispose();
            done.complete(null);
        });
        done.get(10, TimeUnit.SECONDS);
        error.complete(null);

        assertNull(error.get(), "Error while the selection moved to the row of a stopped channel");
    }
}
