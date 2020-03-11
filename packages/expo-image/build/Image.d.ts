import { AccessibilityProps, ImageSourcePropType, ImageStyle, StyleProp } from 'react-native';
export interface ImageProps extends AccessibilityProps {
    source?: ImageSourcePropType | null;
    style?: StyleProp<ImageStyle>;
}
export default function Image(props: ImageProps): JSX.Element;
