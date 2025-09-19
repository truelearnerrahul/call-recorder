import { Redirect, Route } from 'react-router-dom';
import { IonApp, IonFab, IonFabButton, IonIcon, IonRouterOutlet, IonTabBar, IonTabButton, IonTabs, setupIonicReact } from '@ionic/react';
import { IonReactRouter } from '@ionic/react-router';
import Home from './pages/Home';

/* Core CSS required for Ionic components to work properly */
import '@ionic/react/css/core.css';

/* Basic CSS for apps built with Ionic */
import '@ionic/react/css/normalize.css';
import '@ionic/react/css/structure.css';
import '@ionic/react/css/typography.css';

/* Optional CSS utils that can be commented out */
import '@ionic/react/css/padding.css';
import '@ionic/react/css/float-elements.css';
import '@ionic/react/css/text-alignment.css';
import '@ionic/react/css/text-transformation.css';
import '@ionic/react/css/flex-utils.css';
import '@ionic/react/css/display.css';
import './global.css'

/**
 * Ionic Dark Mode
 * -----------------------------------------------------
 * For more info, please see:
 * https://ionicframework.com/docs/theming/dark-mode
 */

/* import '@ionic/react/css/palettes/dark.always.css'; */
/* import '@ionic/react/css/palettes/dark.class.css'; */
import '@ionic/react/css/palettes/dark.system.css';

/* Theme variables */
import './theme/variables.css';
import Recordings from './pages/Recordings';
import { call, home, keypad, mic, people } from 'ionicons/icons';
import { ContactsPage } from './pages/ContactsPage';
import { Recents } from './pages/Recents';
import { StatusBar } from '@capacitor/status-bar';

setupIonicReact();
StatusBar.setOverlaysWebView({ overlay: false });

const App: React.FC = () => (

  <IonApp>
    <IonReactRouter>
    <IonTabs>
      <IonRouterOutlet>
        <Redirect exact path="/" to="/home" />

        <Route path="/home" render={() => <Home />} exact={true} />
        <Route path="/recents" render={() => <Recents />} exact={true} />
        <Route path="/contacts" render={() => <ContactsPage />} exact={true} />
        <Route path="/recordings" render={() => <Recordings />} exact={true} />
      </IonRouterOutlet>


      <IonTabBar slot="bottom">
        <IonTabButton tab="home" href='/home'>
          <IonIcon icon={home} />
          Home
        </IonTabButton>
        <IonTabButton tab="recents" href='/recents'>
          <IonIcon icon={call} />
          Recents
        </IonTabButton>
        <IonTabButton tab="contacts" href='/contacts'>
          <IonIcon icon={people} />
          Contacts
        </IonTabButton>
        <IonTabButton tab="recordings" href='/recordings'>
          <IonIcon icon={mic} />
          Call Recordings
        </IonTabButton>
      </IonTabBar>
    </IonTabs>
    </IonReactRouter>
    <IonFab slot='fixed' horizontal='end' vertical='bottom'>
      <IonFabButton color="primary" routerLink="/dialpad">
        <IonIcon icon={keypad} />
      </IonFabButton>
    </IonFab>

  </IonApp>
);

export default App;
