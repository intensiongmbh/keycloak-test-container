package de.intension.keycloak.test;

import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;

public class KeycloakDevRunner
{

    public static void main(String[] args)
        throws Exception
    {
        try (var kc = new KeycloakDevContainer("jahr-media-custom-rest-api")) {
            kc.withFixedExposedPort(8080, 8080);
            kc.withFixedExposedPort(8787, 8787);
            kc.withClassFolderChangeTrackingEnabled(true);
            kc.withRealmImportFile("realm-export.json");
            kc.start();

            class StdoutConsumer extends BaseConsumer<StdoutConsumer>
            {

                @Override
                public void accept(OutputFrame outputFrame)
                {
                    System.out.print(outputFrame.getUtf8String());
                }
            }
            kc.followOutput(new StdoutConsumer().withRemoveAnsiCodes(true));

            System.out.println("Keycloak Running, you can now attach your remote debugger!");
            System.in.read();
        }
    }
}
